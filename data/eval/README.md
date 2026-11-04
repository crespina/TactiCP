# NL → DSL translation evaluation

Evaluates the natural-language → DSL translator (`LlmQuery`, the same path the
webapp's `/translate` endpoint uses) against a set of reference translations.

For each `(natural-language query, gold DSL)` pair the pipeline:

1. **Translates** the NL query with `LlmQuery` (configurable model / temperature /
   retries).
2. **Runs both** the gold DSL and the generated DSL on the same dataset
   (`--eval-dataset`, default `all`). As in the webapp — where the user selects the
   games and the `FROM(...)` is added automatically — the dataset is *not* part of
   what the LLM must translate: the pairs should **not** include a `FROM(...)`, and
   the pipeline injects `.FROM("<eval-dataset>")` into both queries before running
   (any `FROM` a query does contain is replaced).
3. **Compares outputs** with strict, order-independent match-set equality
   (each match's instance + every event's `[start,end,length]` interval +
   player/ball ids). `COUNT()` queries are compared by their count.
4. On any failure, **diffs the two ASTs** (`AstAnalyzer`) to localise where the
   generated query first diverges: `ENTITY`, `EVENT`, `COMBINATOR`, `MINRANGE`,
   `WHERE`, `SEARCHMODE`, `STRUCTURE`. AST analysis runs only on failing pairs.

Every pair becomes one row in an output CSV, and a summary is printed at the end.

## Input format

A JSON array of objects. `nl` is the natural-language query, `dsl` is the correct
("gold") DSL translation; `id` is optional. As with the webapp, do **not** put a
`FROM(...)` in the gold `dsl` — the game set is chosen at evaluation time via
`--eval-dataset` and injected automatically.

```json
[
  { "id": "pass-any",
    "nl": "Find all passes from any player to any other player.",
    "dsl": "SELECT(PLAYER(\"p1\").PASSTO(PLAYER(\"p2\")).MINRANGE())" }
]
```

Key aliases are accepted: `nl` | `query` | `human` | `natural_language` | `input`
| `question`, and `dsl` | `gold` | `translation` | `target` | `expected` |
`output`. A top-level `{ "pairs": [...] }` object is also accepted.

### The two input files in this folder

| file | what it is |
|---|---|
| `query_pairs_sample.json` | small sample built from the translator's own reference examples. **Not an evaluation set** — 11 of its 12 gold queries are verbatim `SYSTEM_PROMPT` examples and the 12th is one of them with different literals, so it measures recall of the prompt, not translation. Keep it for harness smoke tests (`--identity`) only. |
| `query_pairs.json` | a **DSL coverage corpus**: 185 gold queries graded from level 1 (a single atomic event) to level 10 (full-stack combinations), spanning every construct the DSL can express and many combinations of them. Every pair has both `nl` and `dsl`. This is the evaluation set. |

Edit `query_pairs.json` directly — it is the only copy. (It began as the output
of a `gen_query_pairs.py` that is no longer in the tree; the `nl` side was written
by hand afterwards, so regenerating it would destroy that work.) Besides
`id`/`nl`/`dsl` each entry carries two extra keys the pipeline ignores but which
make the corpus easy to slice: `level` (1-10) and `features` (the constructs the
query exercises, e.g. `["AND:3","NOT","WHERE:global","SEARCH:k"]`).

### No overlap with the prompt

The corpus is kept disjoint from the worked examples in `LlmQuery.SYSTEM_PROMPT`:
a query the model has been shown verbatim measures memorisation, not translation.
16 such queries were removed while the corpus was built; no DSL construct lost
coverage as a result, the thinnest affected one retaining three instances. Two pairs
remain that share a prompt example's *shape* with different zones/IDs, which is fair
game.
Re-check after any change to the corpus or to the prompt — the audit re-extracts
the prompt from `LlmQuery.java`, so it cannot drift:

```bash
python3 data/eval/tools/check_contamination.py data/eval/query_pairs.json
```

Every query in it has been checked two ways: it builds via
`QueryEvaluator.parseStructure`, **and** it executes to completion on one game
(`SNGS-060`) inside a 45 s budget: 185/185, in under 30 s for the whole sweep. That
second check matters: `checkSyntax` alone accepts several constructs that only fail
once the solver runs them.

Both checks are reproducible with the two helpers in `tools/`. They take a file
of one DSL query per line (no `FROM` — they inject it), so:

```bash
CP="target/classes:$(cat target/eval-classpath.txt)"
javac -cp "$CP" -d /tmp/dslcheck data/eval/tools/*.java
python3 -c "import json;print('\n'.join(e['dsl'] for e in json.load(open('data/eval/query_pairs.json'))))" > /tmp/queries.txt

# 1. does every query build?  (fast, loads no game data)
java -cp "$CP:/tmp/dslcheck" CheckSyntax /tmp/queries.txt

# 2. does every query actually run?  (args: file, dataset, per-query timeout in s)
java -Xmx6g -cp "$CP:/tmp/dslcheck" RunCorpus /tmp/queries.txt SNGS-060 45
```

`RunCorpus` prints one line per query (`ok`/`FAIL`, elapsed ms, match count or
error) and gives each query its own timeout, so one runaway query does not stop
the sweep.

The prompt and the corpus now agree on the two points this note used to raise:
sides are written `TEAM("left")` / `TEAM("right")`, and `AND`/`OR` may be nested
(the engine runs nested groups, and `NOT` on a group follows De Morgan's laws).
The pairs tagged `NESTED` and `DEMORGAN` exercise these.

## Running

```bash
./run_eval.sh --input data/eval/query_pairs_sample.json
```

Flags:

| flag | default | meaning |
|------|---------|---------|
| `--input <path>` | *(required)* | pairs JSON |
| `--output <path>` | `eval_results.csv` | CSV output |
| `--model <id>` | `openai/gpt-oss-120b` | model name, as the endpoint knows it |
| `--endpoint <url>` | Groq, or `$LLM_ENDPOINT` | any OpenAI-compatible chat-completions URL |
| `--local` | off | shorthand for `--endpoint http://localhost:8000/v1/chat/completions` (a local vLLM server) |
| `--temperature <double>` | `0.0` | sampling temperature |
| `--max-retries <int>` | `3` | translate attempts (each retry feeds back the syntax error) |
| `--eval-dataset <string>` | `all` | dataset **both** queries run on |
| `--timeout-seconds <int>` | `120` | wall-clock limit per query and per game (`0` = none) |
| `--limit <int>` | *(all)* | only the first N pairs |
| `--identity` | off | skip the LLM, use gold as "generated" (harness self-test) |
| `--judge` | off | also record a second LLM's 0/1 opinion of each translation |
| `--judge-model <id>` | same as `--model` | model for that second opinion (implies `--judge`) |
| `--resume` | off | reuse the rows already in `--output`; evaluate only the rest |
| `--redo <STATUS,…>` | `CONFIG_ERROR` | with `--resume`, re-run pairs recorded with these statuses |

## A second opinion (`--judge`)

`--judge` adds a second, independent `LlmQuery` instance to the run. It holds no
conversation with the translator: it is given a reviewing prompt and one message
containing the request and the query produced from it, and nothing else — not the
gold DSL, not the translator's prompt, not its retries or errors. It answers with
a single character, `1` (good translation) or `0` (not), recorded in the
`judge_verdict` column. A blank cell means it was not asked, or gave no usable
answer (the call is logged as a `WARN` and never fails the run).

```bash
./run_eval.sh --input data/eval/query_pairs_sample.json --judge-model openai/gpt-oss-120b
```

**It decides nothing.** Pass/fail still comes only from running both queries. The
judge is worth having for the pairs where the two disagree: a `PASS` the judge
calls `0` is usually a gold query that is not what the English asks for, and an
`OUTPUT_MISMATCH` the judge calls `1` is usually a translation that is right in a
way the strict interval comparison cannot see. The summary prints how often the
two agree.

Model choice matters more than for translation. On a five-case probe
(three correct translations, one missing `NOT`, one `AND` where the English says
"and then"), `openai/gpt-oss-120b` got all five right while `openai/gpt-oss-20b`
called two correct queries bad — it distrusts anonymous players such as
`PLAYER("p1").PASSTO(PLAYER("p2"))`. Since `--judge-model` defaults to whatever
`--model` is, set it explicitly.

Two notes. The judge never sees a `FROM(...)` clause, because the pairs do not
carry one; its prompt is explicit that this is expected, and an earlier version
that left this out answered `0` to nearly everything. And under `--resume`, a row
that has a generated DSL but no verdict is re-evaluated when `--judge` is on, so
the column does not end up half filled.

Because `--identity` feeds the gold DSL through as the "generated" one, running
`--identity --judge` asks the judge to check the corpus itself — every
`judge_verdict = 0` is a gold pair whose DSL may not match its hand-written `nl`.
That costs one judge call per pair and no translation calls.

## Resuming an interrupted run

Rows are written and flushed **as each pair finishes**, so the CSV is always a
complete record of the work done so far. A run killed after 90 of 188 pairs —
Ctrl-C, an OOM, a dropped API key — is picked up with the same command plus
`--resume`:

```bash
./run_eval.sh --input data/eval/query_pairs.json --output eval_results.csv --resume
```

A stored row is reused only if it describes the same work: same `id`,
`nl_query` and `gold_dsl` (so an edited corpus is re-evaluated instead of being
reported with stale numbers) **and** the same `model`, `temperature`,
`max_retries` and `eval_dataset`. Anything else — a pair no longer in the input,
a duplicate, a row half-written when the process died — is dropped and the file
is rewritten from the rows that survive, so the CSV never ends up with two rows
for one pair. Resumed pairs are marked `[resumed from …]` in the console log and
counted in the summary.

To re-run pairs that failed for a transient reason while keeping the rest:

```bash
./run_eval.sh --input … --resume --redo TRANSLATION_FAILED,TIMEOUT
```

Two caveats. `--identity` is not recorded in the CSV, so a self-test and a real
run must not share an output file. And because rows are now written as they are
produced, starting a run **truncates** `--output` immediately rather than at the
end — pass `--resume`, or a different `--output`, if you want to keep what is
there.

> **Performance:** `--eval-dataset all` loads every game in `train`+`test`+`valid`
> for *both* queries of *every* pair. For quick iteration use a single game
> (`--eval-dataset SNGS-060`) or a split (`--eval-dataset test`).

Needs `GROQ_API_KEY` (environment or `.env`) when the endpoint is Groq's, which is the
default. `--identity`, `--local` and any other `--endpoint` need no key.

## Output columns

`overall_status` is one of `PASS`, `TRANSLATION_FAILED`, `GOLD_ERROR`,
`EXEC_ERROR`, `TIMEOUT`, `OUTPUT_MISMATCH`, `CONFIG_ERROR`. On failure, `fail_category` +
`fail_detail` say where the divergence is. Other columns record the generated
DSL, retry count, per-query result kind/count, per-step timings, and the run
configuration (model / temperature / retries / dataset).

## Results of the paper

`results/` holds one run per model on `query_pairs.json`, all with
`--temperature 0.0 --max-retries 3 --eval-dataset all --timeout-seconds 1000`:

| file | model | endpoint |
|---|---|---|
| `results/qwen3-4b-instruct-2507.csv` | `Qwen/Qwen3-4B-Instruct-2507` | vLLM on a local GPU (`--local`) |
| `results/gpt-oss-120b.csv` | `openai/gpt-oss-120b` | Groq |
| `results/claude-opus-5-5.csv` | `claude-opus-5-5` | `tools/claude_shim.py` |

`tools/claude_shim.py` is an OpenAI-compatible endpoint that answers each request with
Claude Code's headless mode (`claude -p`), with its tools, MCP servers, project files
and its own system prompt switched off: the model sees the harness's system prompt and
messages and nothing else. No sampling temperature can be set this way. Start it, then
point the harness at it:

```bash
python3 data/eval/tools/claude_shim.py --check      # shows what the model receives
python3 data/eval/tools/claude_shim.py --effort high &
./run_eval.sh --input data/eval/query_pairs.json --output claude.csv \
  --model claude-opus-5-5 --endpoint http://127.0.0.1:8765/v1/chat/completions \
  --temperature 0.0 --max-retries 3 --eval-dataset all --timeout-seconds 1000
```
