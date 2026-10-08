#!/usr/bin/env bash
# Runs the :pipeline promptEval over the models in scripts/eval-models.txt (or --models) (Ollama and LM Studio), one model resident at a time and
# one gradle at a time, then prints a comparison table from build/reports/promptEval/*.json.
#   scripts/eval-matrix.sh [--stability N] [--only PREFIX] [--table-only] [--models "ollama:gemma4:e4b-mlx lmstudio:google/gemma-4-e4b"]
#                          [--rules generic] [--langs all|fr,de,...] [--suite batch|words|realrun|sequence|prescan|terms|setup|consistency|retry] [--batch-sizes 4,8,12,16]
# --rules generic forces every prompt to the generic language rules (reports end in -generic.json), so run the matrix
# once without it and once with it and compare the two rows of each model in the table. --langs runs the per-language
# mini-corpora (eval/languages/<tag>.json) instead of the English -> Ukrainian case set. --suite batch runs the batch
# draft A/B instead (batches of 4/8/12/16 consecutive cases through the JSON batch protocol; reports end in
# -batch.json) and prints one row per model and batch size. --suite words runs the garbled-word check (ADR-0042) over
# eval/words.json (reports end in -words.json) and prints recall and false positives per model. --suite realrun runs the
# real-run corpus (15e.2: quotes, scripts, narrator, short lines, batch protocol, reviewer batches, repairs; reports end in
# -realrun.json) and prints the share of cases right per kind and call, the known failures beside them, the truncated
# reviewer calls and the too-short and leaked batch items; --stability repeats the reviewer calls, --only is a case-id prefix.
# --suite sequence runs a generated synthetic book (8 chapters, ~320 paragraphs; 15e.3; reports end in
# -sequence-<narrator>.json) through the real job and prints one row per model and narrator mode: distinct renderings per
# term, name variants, narrator-gender slips, English leftovers, quote failures, flagged rate, first-round hard-gate
# failures, leaked protocol, truncated reviewer replies, wrongly shared renderings, batch fallback rate, calls and seconds per
# segment. --narrator unset|set|detect|both (default unset = the real run's brief with no narrator; set = first-person male;
# detect = the unset brief plus what Start translation does: the narrator detector reads the source and the answer to "who
# narrates" is --narrator-gender male|female, male by default) chooses the brief; both runs each model with unset and set. BOOKLOOM_EVAL_DIAL (default BALANCED) and BOOKLOOM_EVAL_WINDOW pass
# through. A run takes about 15-25 min on a small model and 40-70 min on a 26B class model, so the per-model timeout
# (MODEL_TIMEOUT, seconds) defaults to 5400 for this suite and 1500 for the others, per narrator mode; set it to override.
# scripts/eval-models.txt lines are `<provider>:<model> [context-length|-] [label]`. An LM Studio model is loaded with
# `lms load --context-length <N>` (default 16384) and, when a label is given, under the identifier `<model>-<label>`, so the
# same model in two quants (or two context lengths) gives two reports. --models takes the same entries with the columns
# joined by commas: "lmstudio:google/gemma-4-e4b,32768,q8". The brief the eval sends is BOOKLOOM_EVAL_PRESET (e.g.
# burning-chrome), BOOKLOOM_EVAL_BRIEF (a JSON file) or BOOKLOOM_EVAL_REGISTER / _NAMES / _GENRE / _DIAL; they pass through.
# The window is the model's reported context length capped at 16384 (as a run does) unless BOOKLOOM_EVAL_WINDOW is set.
# --suite prescan|terms|setup|consistency|retry runs the model-call stages the other suites never reach (A4), each through
# its production class on small invented fixtures (eval/stages/*.json; reports end in -<suite>.json): prescan = the glossary
# name scan, terms = the recurring-term choice and the glossary review, setup = the file-name and Book Brief suggestions,
# consistency = the export pass (retry of doubted segments, check against the neighbours), retry = the review desk's Retry.
# One row per model and suite: share of cases right, calls, failed and repeated calls, refused answers.
# Gradle's output is kept in build/eval-matrix/<model>-<narrator>.log; its key lines are shown when a model fails.
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
NARRATOR=unset
NARRATOR_GENDER=male
DEFAULT_CONTEXT=16384
# An entry is model,context,label: the columns of a line of eval-models.txt joined by commas ("-" for no context).
entries() { awk '{ print $1 "," ($2 == "" ? "-" : $2) "," $3 }' | tr '\n' ' '; }
MODELS=$(grep -vE '^\s*(#|$)' scripts/eval-models.txt | entries)

while [ $# -gt 0 ]; do
  case "$1" in
    --stability) STABILITY=$2; shift 2 ;;
    --only) ONLY=$2; shift 2 ;;
    --models) MODELS=$(echo "$2" | tr ' ' '\n' | sed 's/,/ /g' | entries); shift 2 ;;
    --table-only) TABLE_ONLY=1; shift ;;
    --rules) RULES=$2; shift 2 ;;
    --langs) LANGS=$2; shift 2 ;;
    --suite) SUITE=$2; shift 2 ;;
    --batch-sizes) BATCH_SIZES=$2; shift 2 ;;
    --narrator) NARRATOR=$2; shift 2 ;;
    --narrator-gender) NARRATOR_GENDER=$2; shift 2 ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done

REPORTS=modules/pipeline/build/reports/promptEval
if [ "$SUITE" = sequence ]; then DEFAULT_TIMEOUT=5400; else DEFAULT_TIMEOUT=1500; fi

run_one() {
  local entry=$1 narrator=${2:-unset} spec context label provider model api_model url env_provider log
  IFS=, read -r spec context label <<<"$entry"
  provider=${spec%%:*}; model=${spec#*:}; api_model=$model
  [ "$context" = "-" ] && context=""
  if [ "$provider" = lmstudio ]; then
    url=$LMSTUDIO_URL; env_provider=lmstudio
    [ -n "$label" ] && api_model="$model-$label"
    lms unload --all >/dev/null 2>&1
    local load=(lms load "$model" --context-length "${context:-$DEFAULT_CONTEXT}" -y)
    [ -n "$label" ] && load+=(--identifier "$api_model")
    "${load[@]}" || { echo "!! cannot load $model (${load[*]})"; return; }
  else
    url=$OLLAMA_URL; env_provider=ollama
  fi
  mkdir -p build/eval-matrix
  log="build/eval-matrix/$(echo "$api_model-$narrator" | tr -c 'A-Za-z0-9._\n-' '_').log"
  echo "== $provider $api_model context=${context:-$DEFAULT_CONTEXT} narrator=$narrator (gradle log: $log)"
  BOOKLOOM_EVAL_URL=$url BOOKLOOM_EVAL_PROVIDER=$env_provider BOOKLOOM_EVAL_MODEL=$api_model \
    BOOKLOOM_EVAL_ONLY=$ONLY BOOKLOOM_EVAL_STABILITY=$STABILITY BOOKLOOM_EVAL_RULES=$RULES BOOKLOOM_EVAL_LANGS=$LANGS BOOKLOOM_EVAL_SUITE=$SUITE BOOKLOOM_EVAL_NARRATOR=$narrator BOOKLOOM_EVAL_NARRATOR_GENDER=$NARRATOR_GENDER BOOKLOOM_EVAL_BATCH_SIZES=${BATCH_SIZES:-4,8,12,16} ./gradlew :pipeline:promptEval >"$log" 2>&1 &
  local pid=$!
  ( sleep "${MODEL_TIMEOUT:-$DEFAULT_TIMEOUT}"; pkill -P "$pid" 2>/dev/null; kill "$pid" 2>/dev/null; pkill -f "Gradle Test Executor" 2>/dev/null ) &
  local dog=$!
  if ! wait "$pid"; then
    echo "   !! gradle failed, a threshold was missed, or the run timed out; what $log says:"
    grep -E "Eval window|FAILED|AssertionError|Exception|Expecting" "$log" | head -15 | sed 's/^/   | /'
    tail -5 "$log" | sed 's/^/   | /'
  fi
  kill "$dog" 2>/dev/null
  if [ "$provider" = lmstudio ]; then lms unload --all >/dev/null 2>&1; else ollama stop "$model" >/dev/null 2>&1; fi
}

if [ "$TABLE_ONLY" = 0 ]; then
  if [ "$NARRATOR" = both ]; then MODES="unset set"; else MODES=$NARRATOR; fi
  for m in $MODELS; do
    if [ "$SUITE" = sequence ]; then for n in $MODES; do run_one "$m" "$n"; done; else run_one "$m"; fi
  done
fi

python3 - "$REPORTS" "$SUITE" <<'PY'
import glob, json, sys
reports = [json.load(open(f)) for f in sorted(glob.glob(sys.argv[1] + "/*.json"))]
if sys.argv[2] == "batch":
    print("%-36s %4s %5s %8s %8s %8s %8s %8s %8s %8s" % ("model", "size", "calls", "idValid", "tokGate", "omit", "merge", "tooShort", "leaked", "outTok/i"))
    for r in (r for r in reports if r.get("suite") == "batch"):
        for c in r["cells"]:
            print("%-36s %4d %5d %7.0f%% %7.0f%% %7.1f%% %7.1f%% %7.1f%% %7.1f%% %8.1f" % (r["model"], c["size"], c["calls"], 100 * c["idValidity"], 100 * c["tokenGate"], 100 * c["omission"], 100 * c["merge"], 100 * c.get("tooShort", 0), 100 * c.get("leaked", 0), c["outputTokensPerItem"]))
    sys.exit(0)
if sys.argv[2] == "realrun":
    runs = [r for r in reports if r.get("suite") == "realrun"]
    keys = []
    for r in runs:
        keys += [g["key"] for g in r["groups"] if g["key"] not in keys]
    print("%-36s " % "model" + " ".join("%s" % k for k in keys))
    for r in runs:
        by = {g["key"]: g for g in r["groups"]}
        cells = ["%*s" % (len(k), ("%.0f%%" % (100 * by[k]["rate"]) + ("+%dk" % by[k]["known"] if by[k]["known"] else "")) if k in by else "-") for k in keys]
        print("%-36s " % r["model"] + " ".join(cells))
    print()
    print("%-36s %9s %8s %7s %9s" % ("model", "truncated", "tooShort", "leaked", "stability"))
    for r in runs:
        print("%-36s %9d %7.1f%% %6.1f%% %8.0f%%" % (r["model"], r.get("truncated", 0), 100 * r.get("tooShort", 0), 100 * r.get("leaked", 0), 100 * r.get("stability", 0)))
    print("(+Nk: N known failures reported beside the rate, see tasks.md 15e.2)")
    sys.exit(0)
if sys.argv[2] == "sequence":
    runs = sorted((r for r in reports if r.get("suite") == "sequence"), key=lambda r: (r.get("model", ""), r.get("narrator", "")))
    print("%-30s %-6s %-8s %5s %7s %6s %7s %7s %6s %6s %6s %6s %7s %7s %6s %7s" % ("model", "narr", "dial", "segs", "rend/tm", "nameV", "gender", "english", "quote", "ascii", "mixed", "flag%", "noTgt", "fallbk%", "calls", "sec/seg"))
    for r in runs:
        g = lambda k, r=r: r.get(k, 0)
        print("%-30s %-6s %-8s %5d %7.2f %6d %7d %7d %6d %6d %6d %5.1f%% %7d %6.1f%% %6.2f %7.2f" % (r.get("model", "?"), r.get("narrator", "?"), r.get("dial", "?"), g("segments"), g("renderingsPerTerm"), g("nameVariants"), g("genderSlips"), g("englishLeftovers"), g("quoteFailures"), g("asciiQuotes"), g("mixedScript"), 100.0 * g("flagged") / max(1, g("segments")), g("flaggedWithoutTarget"), 100 * g("batchFallbackRate"), g("callsPerSegment"), g("secondsPerSegment")))
    print()
    print("%-30s %-6s %7s %7s %8s %8s %8s %8s %8s  %s" % ("model", "narr", "hard0", "leaked", "revTrunc", "claimed", "domin%", "learned", "cover", "per term: distinct renderings (dominant share)"))
    for r in runs:
        print("%-30s %-6s %7d %7d %8d %8d %7.0f%% %8d %7.0f%%  %s" % (r.get("model", "?"), r.get("narrator", "?"), r.get("hardGateFailuresRound0", 0), r.get("leakedProtocol", 0), r.get("reviewerTruncated", 0), r.get("termClaimedWrong", 0), 100 * r.get("dominantShareMean", 0), r.get("learned", 0), 100 * r.get("learnedCoverage", 0), " ".join("%s=%d(%.0f%%)" % (t.get("term", "?"), t.get("distinct", 0), 100 * t.get("dominantShare", 0)) for t in r.get("terms", []))))
    print("(rend/tm: mean distinct renderings per fixture term, 1.00 is ideal; gender: narrator slips in the first-person chapters; hard0: segments whose first round failed a hard gate; claimed: terms sharing a learned rendering)")
    sys.exit(0)
if sys.argv[2] == "words":
    print("%-36s %5s %8s %8s %6s" % ("model", "cases", "recall", "falsePos", "ok"))
    for r in (r for r in reports if r.get("suite") == "words"):
        print("%-36s %5d %7.0f%% %7.0f%% %6s" % (r["model"], r["cases"], 100 * r["recall"], 100 * r["falsePositive"], "yes" if r["meetsTarget"] else "NO"))
    sys.exit(0)
STAGES = ("prescan", "terms", "setup", "consistency", "retry")
if sys.argv[2] in STAGES:
    print("%-36s %-12s %5s %6s %6s %6s %8s %7s" % ("model", "suite", "cases", "pass", "calls", "failed", "repeated", "refused"))
    for r in (r for r in reports if r.get("suite") == sys.argv[2]):
        print("%-36s %-12s %5d %5.0f%% %6d %6d %8d %7d" % (r["model"], r["suite"], r["cases"], 100 * r["passRate"], r["calls"], r["failed"], r["repeated"], r["refused"]))
    sys.exit(0)
rows = [r for r in reports if r.get("suite") not in ("batch", "words", "realrun", "sequence") + STAGES]
cols = ["parse", "gate", "script", "marker", "injection", "reviewSeparation", "reviewParse", "falseNegative", "falsePositive", "stability"]
print("%-36s %-8s %-6s " % ("model", "rules", "class") + " ".join("%7s" % c[:7] for c in cols) + " tokBrk  ok")
for r in rows:
    print("%-36s %-8s %-6s " % (r["model"], r.get("rules", "language"), r["class"]) + " ".join("%6.0f%%" % (100 * r[c]) for c in cols) + " %6d  " % r.get("tokenBreaks", 0) + ("yes" if r["meetsThresholds"] else "NO"))
kinds = []
for r in rows:
    kinds += [k for k in r.get("byKind", {}) if k not in kinds]
if kinds:
    print()
    print("%-36s " % "corpus right per kind" + " ".join("%s" % k for k in kinds))
    for r in rows:
        print("%-36s " % r["model"] + " ".join("%*s" % (len(k), "%.0f%%" % (100 * r["byKind"][k]) if k in r.get("byKind", {}) else "-") for k in kinds))
PY
