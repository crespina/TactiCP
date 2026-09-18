#!/usr/bin/env bash
#
# Runs the natural-language -> DSL evaluation pipeline
# (org.main.sn.eval.EvaluationPipeline).
#
# We invoke java directly rather than `mvn exec:java` because the POM hardcodes
# the exec plugin's <mainClass> (the JavaFX launcher), which cannot be overridden
# with -Dexec.mainClass.
#
# Examples:
#   ./run_eval.sh --input data/eval/query_pairs_sample.json
#   ./run_eval.sh --input my_pairs.json --model llama-3.1-8b-instant --temperature 0.2 --max-retries 5
#   ./run_eval.sh --input my_pairs.json --eval-dataset SNGS-060      # faster than "all"
#   ./run_eval.sh --input data/eval/query_pairs_sample.json --identity      # self-test, no API calls
#   ./run_eval.sh --input my_pairs.json --judge-model openai/gpt-oss-120b  # + a 0/1 opinion
#   ./run_eval.sh --input my_pairs.json --resume            # continue an interrupted run
#   ./run_eval.sh --input my_pairs.json --resume --redo TRANSLATION_FAILED,TIMEOUT
#
# Requires GROQ_API_KEY in the environment or in a .env file (unless --identity).
set -euo pipefail
cd "$(dirname "$0")"

CP_FILE=target/eval-classpath.txt

echo ">> compiling..." >&2
mvn -q -DskipTests compile
if [ ! -f "$CP_FILE" ] || [ pom.xml -nt "$CP_FILE" ]; then
  echo ">> resolving classpath..." >&2
  mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
fi

exec java -cp "target/classes:$(cat "$CP_FILE")" org.main.sn.eval.EvaluationPipeline "$@"
