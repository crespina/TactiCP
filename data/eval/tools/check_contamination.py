#!/usr/bin/env python3
"""Train/test contamination check for the NL->DSL evaluation.

Extracts every DSL query embedded in LlmQuery.SYSTEM_PROMPT and reports which
pairs of an evaluation corpus reuse one, under three increasingly loose notions
of overlap:

  verbatim  - identical modulo the FROM clause and insignificant whitespace
              (the same canonicalisation EvaluationPipeline uses to short-circuit
              a pair as `identical_query`)
  relabelled- identical after replacing player/team name literals by
              first-appearance indices (AstAnalyzer's coreference normalisation)
  structural- identical after replacing every string literal by S and every
              integer by N, i.e. the same query shape with new zones/IDs

Usage: check_contamination.py <pairs.json> [<pairs.json> ...]
Exit status 1 if any verbatim overlap is found.
"""
import json, pathlib, re, sys

JAVA = pathlib.Path('src/main/java/org/main/sn/dsl/LlmQuery.java')
FROM = re.compile(r'\.FROM\s*\(\s*"[^"]*"\s*\)')


def system_prompt(path=JAVA):
    src = path.read_text()
    i = src.index('SYSTEM_PROMPT = """')
    start = src.index('\n', i) + 1
    body = src[start:src.index('"""', start)]
    lines = body.split('\n')
    indent = min((len(l) - len(l.lstrip()) for l in lines if l.strip()), default=0)
    body = '\n'.join(l[indent:] if len(l) > indent else l.strip() for l in lines)
    return body.replace('\\\n', '').replace('\\"', '"')


def canon(dsl):
    """Drop the FROM clause and all whitespace outside string literals."""
    out, in_string = [], False
    for c in FROM.sub('', dsl or ''):
        if in_string:
            out.append(c)
            if c == '"':
                in_string = False
        elif c == '"':
            in_string = True
            out.append(c)
        elif not c.isspace():
            out.append(c)
    return ''.join(out)


def relabelled(dsl):
    seen = {}

    def rep(m):
        name = m.group(1)
        if name in ('left', 'right'):
            return f'"{name}"'
        return '"#%d"' % seen.setdefault(name, len(seen) + 1)

    return re.sub(r'"([^"]*)"', rep, canon(dsl))


def structural(dsl):
    return re.sub(r'\d+', 'N', re.sub(r'"[^"]*"', 'S', canon(dsl)))


def main(argv):
    prompt = system_prompt()
    qa = re.findall(r'^Q:\s*(.+?)\nA:\s*(.+?)$', prompt, re.M)
    prompt_dsl = re.findall(r'SELECT\([^\n]*', prompt)
    sets = {
        'verbatim':   {canon(d) for d in prompt_dsl},
        'relabelled': {relabelled(d) for d in prompt_dsl},
        'structural': {structural(d) for d in prompt_dsl},
    }
    print(f'SYSTEM_PROMPT: {len(qa)} worked examples, '
          f'{len(prompt_dsl)} DSL queries in total\n')

    dirty = False
    for f in argv:
        pairs = json.load(open(f))
        if isinstance(pairs, dict):
            pairs = pairs['pairs']
        hits = {k: [] for k in sets}
        for e in pairs:
            dsl = e.get('dsl') or e.get('gold') or ''
            if canon(dsl) in sets['verbatim']:
                hits['verbatim'].append(e)
            elif relabelled(dsl) in sets['relabelled']:
                hits['relabelled'].append(e)
            elif structural(dsl) in sets['structural']:
                hits['structural'].append(e)
        n = len(pairs)
        print(f'=== {f} ({n} pairs) ===')
        for k in ('verbatim', 'relabelled', 'structural'):
            h = len(hits[k])
            print(f'  {k:11s}: {h:3d}  ({100 * h / n:5.1f}%)')
        clean = n - sum(len(v) for v in hits.values())
        print(f'  {"uncontaminated":11s}: {clean:3d}  ({100 * clean / n:5.1f}%)')
        if hits['verbatim']:
            dirty = True
            print('  verbatim-overlap ids: '
                  + ', '.join(str(e.get('id')) for e in hits['verbatim']))
        print()
    return 1 if dirty else 0


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
