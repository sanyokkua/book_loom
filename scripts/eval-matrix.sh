#!/usr/bin/env bash
# Runs the :pipeline promptEval over a list of local models (Ollama and LM Studio), one model resident at a time and
# one gradle at a time, then prints a comparison table from build/reports/promptEval/*.json.
#   scripts/eval-matrix.sh [--stability N] [--only PREFIX] [--table-only] [--models "ollama:gemma4:e4b-mlx lmstudio:google/gemma-4-e4b"]
set -uo pipefail
cd "$(dirname "$0")/.."

OLLAMA_URL=${OLLAMA_URL:-http://localhost:11434}
LMSTUDIO_URL=${LMSTUDIO_URL:-http://localhost:1234/v1}
STABILITY=1
ONLY=""
TABLE_ONLY=0
MODELS="ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx ollama:gpt-oss:20b ollama:gemma4:12b-mxfp8 ollama:gemma4:12b-mlx
ollama:gemma4:e4b-mxfp8 ollama:gemma4:e2b-mlx ollama:qwen3.8:27b-mlx ollama:muse-glimmer:30b-nvfp4-dflash
lmstudio:google/gemma-4-26b-a4b-qat lmstudio:google/gemma-4-e4b lmstudio:qwen/qwen3-4b-2507 lmstudio:qwen/qwen3-vl-4b"

while [ $# -gt 0 ]; do
  case "$1" in
    --stability) STABILITY=$2; shift 2 ;;
    --only) ONLY=$2; shift 2 ;;
    --models) MODELS=$2; shift 2 ;;
    --table-only) TABLE_ONLY=1; shift ;;
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
    BOOKLOOM_EVAL_ONLY=$ONLY BOOKLOOM_EVAL_STABILITY=$STABILITY ./gradlew -q :pipeline:promptEval >/dev/null 2>&1 \
    || echo "   (below threshold or failed — see $REPORTS)"
  if [ "$provider" = lmstudio ]; then lms unload --all >/dev/null 2>&1; else ollama stop "$model" >/dev/null 2>&1; fi
}

if [ "$TABLE_ONLY" = 0 ]; then
  for m in $MODELS; do run_one "$m"; done
fi

python3 - "$REPORTS" <<'PY'
import glob, json, sys
rows = [json.load(open(f)) for f in sorted(glob.glob(sys.argv[1] + "/*.json"))]
cols = ["parse", "gate", "script", "marker", "injection", "judgeSeparation", "judgeParse", "falseNegative", "falsePositive", "stability"]
print("%-36s %-6s " % ("model", "class") + " ".join("%7s" % c[:7] for c in cols) + "  ok")
for r in rows:
    print("%-36s %-6s " % (r["model"], r["class"]) + " ".join("%6.0f%%" % (100 * r[c]) for c in cols) + "  " + ("yes" if r["meetsThresholds"] else "NO"))
PY
