# TactiCP: football pattern retrieval with constraint programming

TactiCP finds the intervals of frames in football broadcast clips where a tactical pattern occurs,
for example "a pass from player 11 to any player, and then that player keeps the ball inside a
circle around the penalty spot". It works offline, on frame-level detections of the players and the
ball (the SoccerNet Game State Reconstruction annotations):

1. The pattern is written in a small SQL-like query language (`SELECT(...).WHERE(...).FROM(...)`).
2. Each query is compiled into a Constraint Programming model (interval variables, `regular`
   constraints, optional/alternative intervals) and solved with [MaxiCP](https://github.com/aia-uclouvain/maxicp).
   Every solution is one matching interval of frames.
3. A large language model can translate an English question into that query language. The
   translation is checked by running it and comparing its answers with those of a reference query.

This code accompanies the paper *An Offline Neuro-Symbolic Football Pattern Retrieval Approach Using
Constraint Programming* (Augustin Crespin, Pierre Schaus). You can try it online at
<https://tacticp.com>.

## Repository layout

| Path | Contents |
|---|---|
| `src/main/java/org/main/sn/dsl/` | The query language: `Factory` (entry points such as `SELECT`, `PLAYER`, `POSITION`), `SelectExpr` (`WHERE`, `FROM`, `SEARCH`, `COUNT`), `DataRoot` (where the dataset is), and `LlmQuery` (English → query translation, including its system prompt). |
| `src/main/java/org/main/sn/logic/` | The compiler from queries to CP models: `Query` (model building and search), `Possession`, `Position`, `SubstringRegular`, `AlternativeElement`, and the dataset reader `GameStateReconstructionInstance`. |
| `src/main/java/org/main/sn/web/` | The Spring Boot web application (`WebApp`, REST API in `QueryController`, query parsing in `QueryEvaluator`). The front end is `src/main/resources/static/index.html`. |
| `src/main/java/org/main/sn/eval/` | `EvaluationPipeline` and `AstAnalyzer`: the English → query evaluation. |
| `src/main/java/org/main/sn/experiments/` | `Scalability` (scalability benchmark) and `PassPrediction` (pass detection used in the comparison with an action-spotting model). |
| `src/main/python/` | Data generation and plotting scripts for the experiments, `gsrPositionExtractor.py` (draws a clip's positions as a video), and `LlamaVideoTest.ipynb` (the VideoLLaMA 3 test mentioned in the introduction of the paper). |
| `src/test/java/` | JUnit tests. Most of them compare the solver's answers with a direct scan of the data. |
| `data/eval/` | Query corpus (`query_pairs.json`), translation results of the three models (`results/`), and helper tools. See [`data/eval/README.md`](data/eval/README.md). |
| `data/scalability/` | Results of the scalability benchmark. |
| `data/comparison/` | Pass detections of TactiCP (`passes_vm.txt`) and of the action-spotting model (`spotting/`), for the comparison between them. |
| `third-party-licenses/` | Licences of the dependencies and an inventory in [`third-party-licenses/README.md`](third-party-licenses/README.md). |
| `run_eval.sh` | Runs the English → query evaluation. |

## Requirements

- Java 21 and Maven 3.8 or later. MaxiCP is fetched from [JitPack](https://jitpack.io), so the
  first build needs network access.
- Python 3 with `numpy` and `matplotlib` for the experiment scripts. `opencv-python` is needed only
  by the video visualisation scripts.
- The SoccerNet Game State Reconstruction 2024 dataset (see below). The engine needs only the
  annotation files of the 164 clips, about 4 GB.
- For English → query translation only: a [Groq](https://groq.com) API key, or any
  OpenAI-compatible endpoint (for example a local vLLM server).

## Getting the data

Download the `gamestate-2024` dataset as explained in the
[sn-gamestate](https://github.com/SoccerNet/sn-gamestate) repository. Its use is governed by the
SoccerNet data agreement, which you accept when downloading it. Lay it out as follows:

```
data/SoccerNet/gamestate-2024/
├── train/SNGS-060/Labels-GameState.json
├── train/SNGS-061/Labels-GameState.json
├── valid/...
└── test/...
```

The engine reads only `Labels-GameState.json`: the position on the pitch, the team and the
ID of each player and of the ball in every frame. The ID is the same in every frame of a clip
(it is the `track_id` field of the annotations). The web application also plays a clip's video if the clip's folder contains a `vid.mp4`.
You can build it from the frames, for example:

```bash
ffmpeg -framerate 25 -i img1/%06d.jpg -c:v libx264 -pix_fmt yuv420p vid.mp4
```

Everything looks for the dataset in `data/SoccerNet/gamestate-2024`, relative to the working
directory, so run the commands below from the repository root. To keep the data elsewhere, set
`DATA_ROOT=<path>` (or pass `-Ddata.root=<path>` to `java`), `<path>` being the folder that holds
`train`, `valid` and `test`.

## Building

```bash
mvn package -DskipTests
```

This produces `target/TactiCP-1.0-web.jar`, the web application with all its dependencies, and
`target/TactiCP-1.0.jar`, the classes alone.

## Running the web application

```bash
java -jar target/TactiCP-1.0-web.jar
```

Then open <http://localhost:8080>. The page lists the clips found in the dataset, runs queries,
plays the matching intervals over the clip's video, and translates English questions into queries.

To use the translation, put your Groq key in a `.env` file at the repository root (or in the
environment):

```
GROQ_API_KEY=...
```

To use another OpenAI-compatible server instead of Groq, set `LLM_ENDPOINT`, for example
`LLM_ENDPOINT=http://localhost:8000/v1/chat/completions`. The key is then not needed.

The same functions are available over HTTP:

```bash
curl -s localhost:8080/api/query -H 'Content-Type: application/json' \
  -d '{"query": "SELECT(PLAYER(\"p1\", 11).PASSTO(PLAYER(\"p2\")).MINRANGE()).FROM(\"SNGS-060\")"}'

curl -s localhost:8080/api/translate -H 'Content-Type: application/json' \
  -d '{"description": "passes from player 11 while the ball is in zone 3", "dataset": "SNGS-060"}'
```

## Using the query language from Java

```java
import static org.main.sn.dsl.Factory.*;

// Player 11 passes to anyone, then the ball goes from zone 6 to zone 7, in clip SNGS-060.
SELECT(PLAYER("p11", 11).PASSTO(PLAYER("p2")).MINRANGE(),
       BALL().MOVETO(6, 7))
    .FROM("SNGS-060")
    .searchAndPrint();
```

`search()` returns the matches as a list instead of printing them, and `COUNT()` returns their
number.

## The query language in brief

```
SELECT(event, event, ...).WHERE(constraint, ...).FROM("dataset")
```

Events listed in `SELECT` happen one after the other, in that order.

**Entities**

| Syntax | Meaning |
|---|---|
| `PLAYER("p")` | any player; the same name in two events denotes the same player |
| `PLAYER("p", 7)` | the player with ID 7 |
| `PLAYER("p", "left")`, `PLAYER("p", 7, "right")` | a player of the left (right) team |
| `BALL()` | the ball |
| `TEAM("left")`, `TEAM("right")` | all the players of a side |
| `TEAM("t", PLAYER(...), ...)`, `TEAM("t", 7, 9, ...)` | a group of players, given as players or by their IDs |

**Events**

| Syntax | Meaning |
|---|---|
| `PLAYER("p").PASSTO(PLAYER("q"))` | p passes the ball to q |
| `PLAYER("p").MOVETO(a, b)`, `BALL().MOVETO(a, b)` | the player (ball) moves from zone a to zone b |
| `TEAM("t").MOVETO(a, b, k)` | at least k players of the team move from zone a to zone b |
| `POSSESSION(PLAYER(...))`, `POSSESSION(TEAM(...))` | the player (a player of the team) has the ball |
| `POSITION(entity, z1, z2, ...)` | the entity is in one of the zones |
| `POSITION(TEAM("t"), K(k), z1, ...)` | at least k players of the team are in the zones |
| `AND(e1, e2, ...)` | the events hold at the same time |
| `OR(e1, e2, ...)` | one of the events holds |
| `NOT(e)` | `e` does not hold (`POSITION` and `POSSESSION` only; De Morgan's laws on groups) |
| `e.MINRANGE()` | the shortest interval where the event holds instead of the longest one |

The pitch is divided into 14 zones. The four central quadrants are 1 to 4. The bands along the
touchlines are 6, 7, 10 and 11, the corners are 5, 8, 9 and 12, and the two goal areas are 13 and
14. `src/main/python/plotZones.py` draws the map.

**Constraints** (`WHERE`, on one event or on the whole `SELECT`). Coordinates are in metres, with
(0, 0) at the centre of the pitch. Times are in frames, at 25 frames per second.

| Syntax | Meaning |
|---|---|
| `RECTANGLE(x, y, w, h)`, `RADIUS(x, y, r)` | restrict the event to an area of the pitch |
| `STARTMIN(f)`, `STARTMAX(f)`, `ENDMIN(f)`, `ENDMAX(f)` | bounds on the start and end frames |
| `MINDURATION(n)`, `MAXDURATION(n)` | bounds on the length of the interval |
| `DURING(a, b)` | only frames a to b |
| `ATLEAST(n)`, `ATMOST(n)` | at least (at most) n matches in total (`SELECT` only) |

**Datasets** (`FROM`): a clip (`"SNGS-060"`), a list of clips (`"SNGS-060,SNGS-061"`), a split
(`"train"`, `"valid"`, `"test"`) or `"all"`.

`.SEARCH(1)` stops at the first match. The system prompt in `LlmQuery.java` gives the complete
rules, with examples.

## Tests

```bash
mvn test
```

The tests read clips SNGS-021, 022, 025, 049, 060, 061 and 127 of the dataset, and take about
two minutes.

## Reproducing the experiments

### English → query translation

The corpus has 185 English questions with their reference queries, from single events (level 1)
to combinations of every construct (level 10). Each translation is run on the 164 clips and
passes when it returns the same answers as the reference.

```bash
./run_eval.sh --input data/eval/query_pairs.json --output results.csv \
  --model openai/gpt-oss-120b --temperature 0.0 --max-retries 3 \
  --eval-dataset all --timeout-seconds 1000
```

[`data/eval/README.md`](data/eval/README.md) describes the options, the output columns, how to use
a local model (`--local`, `--endpoint`), and how the Claude results were obtained through
`data/eval/tools/claude_shim.py`.

### Scalability

The benchmark runs three queries of increasing difficulty: on 1 to 90 clips searched together, and
on single instances of 750 to 67,500 frames (a 45-minute half). The long instances are built by
repeating clip SNGS-061:

```bash
python3 src/main/python/scalability_generator.py \
    --inputs data/SoccerNet/gamestate-2024/train/SNGS-061 --repeat 90 \
    --output data/SoccerNet/gamestate-2024/scalability --clean

mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" org.main.sn.experiments.Scalability all data/scalability 90

python3 src/main/python/plotExecTimes.py         # exectime3.pdf
python3 src/main/python/truncatedVideoPlot.py    # trunc3.pdf, barplot67.pdf
```

The generated instances take about 1 GB. Loading and solving times are written separately to
`data/scalability/scalability_games.csv` and `scalability_long.csv`.

### Comparison with an action-spotting model

`PassPrediction` detects every pass in every clip. `comparison.py` compares these detections with
those of the winner of the 2023 SoccerNet Ball Action Spotting challenge
([lRomul/ball-action-spotting](https://github.com/lRomul/ball-action-spotting)). It expects that
model's detections, which are in `data/comparison/spotting/<clip>/results_spotting.json`.

```bash
mvn package -DskipTests -Pexperiment-pass
java -jar target/TactiCP-1.0-exp-pass.jar > data/comparison/passes_vm.txt
python3 src/main/python/comparison.py --passes data/comparison/passes_vm.txt
```

## Third-party software

The dependencies and their licences are listed in
[`third-party-licenses/README.md`](third-party-licenses/README.md). The SoccerNet data is not
included and is subject to its own terms.

## Citation

```bibtex
@misc{crespin2026tacticp,
  author = {Crespin, Augustin and Schaus, Pierre},
  title  = {An Offline Neuro-Symbolic Football Pattern Retrieval Approach Using Constraint Programming},
  year   = {2026}
}
```
