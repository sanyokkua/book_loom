#!/usr/bin/env bash
# Runs the :pipeline promptEval over the models in scripts/eval-models.txt (or --models) (Ollama and LM Studio), one model resident at a time and
# one gradle at a time, then prints a comparison table from build/reports/promptEval/*.json.
#   scripts/eval-matrix.sh [--stability N] [--only PREFIX] [--table-only] [--models "ollama:gemma4:e4b-mlx lmstudio:google/gemma-4-e4b"]
#                          [--rules generic] [--langs all|fr,de,...] [--suite batch] [--batch-sizes 4,8,12,16]
# --rules generic forces every prompt to the generic language rules (reports end in -generic.json), so run the matrix
# once without it and once with it and compare the two rows of each model in the table. --langs runs the per-language
# mini-corpora (eval/languages/<tag>.json) instead of the English -> Ukrainian case set. --suite batch runs the batch
# draft A/B instead (batches of 4/8/12/16 consecutive cases through the JSON batch protocol; reports end in
# -batch.json) and prints one row per model and batch size.
set -uo pipefail
cd "$(dirname "$0")/.."

OLLAMA_URL=${OLLAMA_URL:-http://localhost:11434}
LMSTUDIO_URL=${LMSTUDIO_URL:-http://localhost:1234/v1}
STABILITY=1
ONLY=""
TABLE_ONLY=0
RULES=language
LANGS=""
SUITE=""
BATCH_SIZES=""
MODELS=$(grep -vE '^\s*(#|$)' scripts/eval-models.txt | tr '\n' ' ')

while [ $# -gt 0 ]; do
  case "$1" in
    --stability) STABILITY=$2; shift 2 ;;
    --only) ONLY=$2; shift 2 ;;
    --models) MODELS=$2; shift 2 ;;
    --table-only) TABLE_ONLY=1; shift ;;
    --rules) RULES=$2; shift 2 ;;
    --langs) LANGS=$2; shift 2 ;;
    --suite) SUITE=$2; shift 2 ;;
    --batch-sizes) BATCH_SIZES=$2; shift 2 ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done

REPORTS=modules/pipeline/build/reports/promptEval

run_one() {
  local provider=${1%%:*} model=${1#*:} url env_provider
  if [ "$provider" = lmstudio ]; then
    url=$LMSTUDIO_URL; env_provider=lmstudio
    lms unload --all >/dev/null 2>&1; lms load "$model" -y >/dev/null 2>&1 || { echo "!! cannot load $model"; return; }
  else
    url=$OLLAMA_URL; env_provider=ollama
  fi
  echo "== $provider $model"
  BOOKLOOM_EVAL_URL=$url BOOKLOOM_EVAL_PROVIDER=$env_provider BOOKLOOM_EVAL_MODEL=$model \
    BOOKLOOM_EVAL_ONLY=$ONLY BOOKLOOM_EVAL_STABILITY=$STABILITY BOOKLOOM_EVAL_RULES=$RULES BOOKLOOM_EVAL_LANGS=$LANGS BOOKLOOM_EVAL_SUITE=$SUITE BOOKLOOM_EVAL_BATCH_SIZES=${BATCH_SIZES:-4,8,12,16} ./gradlew -q :pipeline:promptEval >/dev/null 2>&1 &
  local pid=$!
  ( sleep "${MODEL_TIMEOUT:-1500}"; pkill -P "$pid" 2>/dev/null; kill "$pid" 2>/dev/null; pkill -f "Gradle Test Executor" 2>/dev/null ) &
  local dog=$!
  wait "$pid" || echo "   (below threshold, failed or timed out — see $REPORTS)"
  kill "$dog" 2>/dev/null
  if [ "$provider" = lmstudio ]; then lms unload --all >/dev/null 2>&1; else ollama stop "$model" >/dev/null 2>&1; fi
}

if [ "$TABLE_ONLY" = 0 ]; then
  for m in $MODELS; do run_one "$m"; done
fi

python3 - "$REPORTS" "$SUITE" <<'PY'
import glob, json, sys
reports = [json.load(open(f)) for f in sorted(glob.glob(sys.argv[1] + "/*.json"))]
if sys.argv[2] == "batch":
    print("%-36s %4s %5s %8s %8s %8s %8s %8s" % ("model", "size", "calls", "idValid", "tokGate", "omit", "merge", "outTok/i"))
    for r in (r for r in reports if r.get("suite") == "batch"):
        for c in r["cells"]:
            print("%-36s %4d %5d %7.0f%% %7.0f%% %7.1f%% %7.1f%% %8.1f" % (r["model"], c["size"], c["calls"], 100 * c["idValidity"], 100 * c["tokenGate"], 100 * c["omission"], 100 * c["merge"], c["outputTokensPerItem"]))
    sys.exit(0)
rows = [r for r in reports if r.get("suite") != "batch"]
cols = ["parse", "gate", "script", "marker", "injection", "judgeSeparation", "judgeParse", "falseNegative", "falsePositive", "stability"]
print("%-36s %-8s %-6s " % ("model", "rules", "class") + " ".join("%7s" % c[:7] for c in cols) + "  ok")
for r in rows:
    print("%-36s %-8s %-6s " % (r["model"], r.get("rules", "language"), r["class"]) + " ".join("%6.0f%%" % (100 * r[c]) for c in cols) + "  " + ("yes" if r["meetsThresholds"] else "NO"))
PY
