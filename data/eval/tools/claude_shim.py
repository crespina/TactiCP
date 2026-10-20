#!/usr/bin/env python3
"""OpenAI-compatible endpoint that answers EvaluationPipeline through `claude -p`.

Lets a Claude subscription be evaluated on exactly what the other models got:
run_eval.sh --endpoint points LlmQuery here, and every chat completion is answered
by Claude Code's headless mode with everything Claude Code would add switched off.
The model sees only the harness's SYSTEM_PROMPT (passed as --system-prompt, which
replaces Claude Code's own prompt) and the harness's user turns:

  --tools ""               no tools: no file access, no web, no way to test a query
  --strict-mcp-config      no MCP servers (plugins, claude.ai connectors)
  --safe-mode              no CLAUDE.md, hooks, plugins, skills or output styles
  empty working directory  no git status, no project CLAUDE.md or auto-memory

A retry arrives as the whole history [system, user, assistant, user(error)] and is
answered by resuming the session that wrote that assistant turn, so the model sees
its own answer and the harness's feedback exactly as the other models did. A
resumed call that fails is never repeated (the failed turn may already be in the
session): it returns 503, which the harness records as "translate call failed".
A failing first attempt is repeated every few minutes once the shim has had one
success, so a run that reaches a usage limit pauses instead of failing every pair.

Usage:
  claude_shim.py --check                    show what the model receives, then exit
  claude_shim.py [--model M] [--effort E]   serve on http://127.0.0.1:8765
  ./run_eval.sh --input <pairs.json> --model M \\
      --endpoint http://127.0.0.1:8765/v1/chat/completions
"""
import argparse, hashlib, json, os, subprocess, sys, tempfile, time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

RETRY_EVERY = 300   # seconds between tries while a first attempt keeps failing

CHECK_SYSTEM = 'CANARY-7f3a: this sentence is the whole system prompt set by the test.'
CHECK_PROMPT = ('Quote verbatim, in order, all text you received before this message: every '
                'system prompt block and any other injected context. Then list the tools you '
                'can call, or say that there are none.')


def command(args, system, session=None, output='json'):
    cmd = [args.claude, '-p', '--system-prompt', system,
           '--model', args.model, '--effort', args.effort,
           '--tools', '', '--strict-mcp-config', '--safe-mode', '--disable-slash-commands',
           '--output-format', output]
    if output == 'stream-json':
        cmd.append('--verbose')
    if session:
        cmd += ['--resume', session]
    return cmd


def environment():
    # An API key in the environment would be billed instead of the subscription;
    # CLAUDECODE is set when the shim is started from inside a Claude Code session.
    env = dict(os.environ)
    for name in ('ANTHROPIC_API_KEY', 'CLAUDECODE'):
        env.pop(name, None)
    return env


def call(args, system, prompt, session=None, output='json'):
    """One claude -p call with the prompt on stdin (never argv, so no flag parsing)."""
    return subprocess.run(command(args, system, session, output), input=prompt, text=True,
                          capture_output=True, cwd=args.workdir, env=environment(),
                          timeout=args.call_timeout)


def ask(args, system, prompt, session):
    """Returns (ok, claude's JSON result, error message)."""
    try:
        p = call(args, system, prompt, session)
    except subprocess.TimeoutExpired:
        return False, {}, f'no answer from claude -p within {args.call_timeout}s'
    try:
        out = json.loads(p.stdout)
    except json.JSONDecodeError:
        out = {}
    if p.returncode == 0 and not out.get('is_error') and isinstance(out.get('result'), str):
        return True, out, None
    return False, out, str(out.get('result') or p.stderr.strip() or p.stdout.strip()
                           or f'claude -p exited with {p.returncode}')


def empty_dir(path):
    path = Path(path or tempfile.mkdtemp(prefix='claude-eval-')).resolve()
    path.mkdir(parents=True, exist_ok=True)
    if any(path.iterdir()):
        sys.exit(f'{path} is not empty')
    for d in (path, *path.parents):
        for name in ('.git', 'CLAUDE.md'):
            if (d / name).exists():
                sys.exit(f'{d / name} would be visible to claude; pick a --workdir outside it')
    return path


class Shim(HTTPServer):
    def __init__(self, args):
        super().__init__(('127.0.0.1', args.port), Handler)
        self.args = args
        self.sessions = {}   # hash of (system, first user turn) -> [session id, assistant turns]
        self.successes = 0
        self.log = open(args.log, 'a', encoding='utf-8')

    def answer(self, system, prompt, session):
        deadline = time.monotonic() + self.args.max_wait * 3600
        while True:
            ok, out, error = ask(self.args, system, prompt, session)
            if ok:
                self.successes += 1
            if ok or session or not self.successes or time.monotonic() > deadline:
                return ok, out, error
            say(f'claude failed: {error[:300]}\n[shim] trying this first attempt again in {RETRY_EVERY}s')
            time.sleep(RETRY_EVERY)

    def record(self, session, attempt, prompt, ok, out, error):
        models = sorted(out.get('modelUsage') or {})
        session = out.get('session_id') or session
        self.log.write(json.dumps({
            'time': time.strftime('%Y-%m-%dT%H:%M:%S'), 'session_id': session, 'attempt': attempt,
            'ok': ok, 'models': models, 'prompt': prompt, 'result': out.get('result') if ok else None,
            'error': error, 'duration_ms': out.get('duration_ms'), 'usage': out.get('usage'),
        }, ensure_ascii=False) + '\n')
        self.log.flush()
        say(f'attempt {attempt} {"ok" if ok else "FAILED"}  session={session}  models={models}'
            + ('' if ok else f'\n[shim]   {error[:300]}'))
        if ok and models and not any(self.args.model in m for m in models):
            say(f'WARNING: answered by {models}, not {self.args.model}')


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def do_POST(self):
        shim = self.server
        try:
            messages = json.loads(self.rfile.read(int(self.headers['Content-Length'])))['messages']
            system, first, last = messages[0], messages[1], messages[-1]
            if (system['role'], first['role'], last['role']) != ('system', 'user', 'user'):
                raise ValueError('expected [system, user, ..., user]')
        except Exception as e:
            return self.send(400, {'error': {'message': f'unexpected request: {e}'}})
        key = hashlib.sha256(json.dumps([system['content'], first['content']]).encode()).hexdigest()
        turns = sum(m['role'] == 'assistant' for m in messages)
        session = None
        if turns:
            known = shim.sessions.get(key)
            if not known or known[1] != turns:
                return self.send(409, {'error': {'message': 'retry of a conversation this shim did not start'}})
            session = known[0]
        ok, out, error = shim.answer(system['content'], last['content'], session)
        shim.record(session, turns + 1, last['content'], ok, out, error)
        if not ok:
            return self.send(503, {'error': {'message': error}})
        shim.sessions[key] = [out.get('session_id'), turns + 1]
        # LlmQuery reads the first "content" after "choices" and does not decode \u
        # escapes, so non-ASCII text goes out as raw UTF-8.
        self.send(200, {'object': 'chat.completion', 'model': shim.args.model,
                        'choices': [{'index': 0, 'finish_reason': 'stop',
                                     'message': {'role': 'assistant', 'content': out['result']}}]})

    def send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


def check(args):
    """One call with a canary system prompt: prints Claude Code's init event (tools,
    MCP servers, skills, model, auth source) and the model's own account of what it
    received, which shows anything Claude Code puts in front of the system prompt."""
    p = call(args, CHECK_SYSTEM, CHECK_PROMPT, output='stream-json')
    for line in p.stdout.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get('type') == 'system' and event.get('subtype') == 'init':
            print('== init event: tools, MCP servers, skills, plugins and agents should be empty ==')
            print(json.dumps(event, indent=2))
        elif event.get('type') == 'result':
            print('\n== what the model says it received ==')
            print(event.get('result'))
    if p.returncode:
        print(p.stderr or p.stdout, file=sys.stderr)
    return p.returncode


def say(message):
    print(f'[shim] {message}', file=sys.stderr, flush=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--model', default='claude-opus-5-5')
    ap.add_argument('--effort', default='high', choices=['low', 'medium', 'high', 'xhigh', 'max'])
    ap.add_argument('--port', type=int, default=8765)
    ap.add_argument('--workdir', help='empty directory claude runs in (default: a new one in /tmp)')
    ap.add_argument('--log', default='claude_shim_log.jsonl', help='one JSON line per claude call')
    ap.add_argument('--max-wait', type=float, default=6,
                    help='hours a failing first attempt keeps being retried (default 6)')
    ap.add_argument('--call-timeout', type=int, default=900,
                    help='seconds before one claude call is abandoned (default 900)')
    ap.add_argument('--claude', default='claude', help='claude executable')
    ap.add_argument('--check', action='store_true', help='show what the model receives, then exit')
    args = ap.parse_args()
    args.workdir = str(empty_dir(args.workdir))
    if 'ANTHROPIC_API_KEY' in os.environ:
        say('ignoring ANTHROPIC_API_KEY so that calls use your subscription login')
    say(f'model={args.model}  effort={args.effort}  workdir={args.workdir}')
    if args.check:
        sys.exit(check(args))
    shim = Shim(args)
    say(f'listening on http://127.0.0.1:{args.port}/v1/chat/completions, logging to {args.log}')
    try:
        shim.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
