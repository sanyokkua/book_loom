#!/usr/bin/env bash
# Runs the :pipeline promptEval over the FIRST model of scripts/eval-models.txt (the e4b target), or every line with
# --all-models, or the entries of --models (Ollama and LM Studio), one model resident at a time and one gradle at a time,
# then prints a comparison table from build/reports/promptEval/*.json.
# THE GRADLE LOCK (15j.J16): each (suite x model) Gradle invocation takes scripts/gradle-lock.sh for its own duration and
# never for the whole loop, so other agents' Gradle runs interleave between models. The lock is not re-entrant: never wrap
# this script in scripts/gradle-lock.sh (or .temporary_context/15i/g.sh, which is the same lock), or it waits for itself.
# --suite book --book-file <path> --out-dir <dir> (BookRunEvalTest) translates one whole book and writes its outputs to <dir>
# (BOOKLOOM_EVAL_SUITE=book, BOOKLOOM_EVAL_BOOK_FILE, BOOKLOOM_EVAL_OUT_DIR); it prints no table. Default timeout 24 h.
# QUICK (15j.J16): --quick (BOOKLOOM_EVAL_QUICK=1) runs the fast subsets: the default suite sends only the ten cases of
# QuickEval.PROMPT_CASES and skips the defects corpus, realrun only the cases marked "quick": true, names one invented book
# (BOOKLOOM_EVAL_BOOK, mayday unless you set it), and sequence is replaced by mini (the script says so). --book-file <path>
# builds a temporary one-file corpus directory (the book and its <name>.gold.json beside it) for BOOKLOOM_CORPUS_DIR, so
# the names and gold suites run one owner book. The loop above it: scripts/probe.sh (one case), --quick, --suite mini, then
# one full run per suite, and the 26b once at the end of a change (docs/DEVELOPMENT.md, "The fast loop").
#   scripts/eval-matrix.sh [--stability N] [--only PREFIX] [--table-only] [--models "ollama:gemma4:e4b-mlx lmstudio:google/gemma-4-e4b"] [--all-models] [--quick] [--book-file PATH] [--out-dir DIR]
#                          [--batch-format json|markers|dual] [--relevant-pairs on|off] [--reasoning NAME=on,...] [--batch-notes on|off] [--rules generic] [--langs all|fr,de,...] [--names transliterate|translate|keep_original] [--suite batch|words|realrun|sequence|prescan|terms|setup|consistency|retry|replay|recall|stability|gold|names|coref|mini|book] [--batch-sizes 4,8,12,16]
# --relevant-pairs on|off (15i.T8) switches the batch prompt's "Related earlier pairs" block for the run (unset keeps the
# default, on); run --suite sequence once with each and compare the two reports (their provenance records relevant_pairs=).
# --reasoning "DRAFT_BATCH_JSON=on,TERM_READING=on" (15i.X2) asks the named calls (PromptName constants, any case) to think
# for the run (Ollama think:true, LM Studio reasoning_effort medium); unset leaves every call off. --batch-notes on asks the
# batch draft for a one-line note before each target. The provenance of a report records both (reasoning=, batchNotes=).
# --rules generic forces every prompt to the generic language rules (reports end in -generic.json), so run the matrix
# once without it and once with it and compare the two rows of each model in the table. --langs runs the per-language
# mini-corpora (eval/languages/<tag>.json) instead of the English -> Ukrainian case set. --suite batch runs the batch
# draft A/B instead (consecutive cases, sized as a job sizes its batches unless --batch-sizes fixes the sweep, through the JSON batch protocol; reports end in
# -batch.json) and prints one row per model and batch size. --suite words runs the garbled-word check over
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
# glass-harbour), BOOKLOOM_EVAL_BRIEF (a JSON file) or BOOKLOOM_EVAL_REGISTER / _NAMES / _GENRE / _DIAL; they pass through.
# The window is the model's reported context length capped at 16384 (as a run does) unless BOOKLOOM_EVAL_WINDOW is set.
# --suite prescan|terms|setup|consistency|retry runs the model-call stages the other suites never reach (A4), each through
# its production class on small invented fixtures (eval/stages/*.json; reports end in -<suite>.json): prescan = the glossary
# name scan, terms = the recurring-term choice and the glossary review, setup = the file-name and Book Brief suggestions,
# consistency = the export pass (retry of doubted segments, check against the neighbours), retry = the review desk's Retry.
# One row per model and suite: share of cases right, calls, failed and repeated calls, refused answers.
# --suite replay sends the calls of a real run's trace log again (BOOKLOOM_EVAL_REPLAY_LOG=<bookloom-trace.log[.gz]>; see
# docs/DEVELOPMENT.md, replay): BOOKLOOM_EVAL_REPLAY_MODE=raw|fast, _MAX_BYTES, _LIMIT, _TARGET pass through the environment.
# --suite recall needs no model (15h.E2): it reads the owner's exported books under BOOKLOOM_RECALL_DIR (outside the
# repository; docs/DEVELOPMENT.md, recall suite), runs every deterministic check and the final audit on each aligned
# segment, and prints recall and precision per detector and per defect class against each book's gold.jsonl. --models
# is ignored; the report is build/reports/promptEval/recall.json.
# --suite stability (15h.E3) runs the Book Brief suggestion and the glossary name review on one committed fixture book
# (BOOKLOOM_EVAL_BOOK=<id>|all, default front-matter-converter) once per seed (BOOKLOOM_EVAL_SEEDS, default 1,2,3) for
# every model, and prints each field's agreement across the seeds (target 80 % or more), its accuracy against the
# book's gold, and the agreement of all models' answers pooled. --suite gold scores the same two steps on the owner's
# books (BOOKLOOM_CORPUS_DIR holding <name>.gold.json files; docs/DEVELOPMENT.md, judgement evals).
# --suite names (15i.E1) runs Names & style end to end (scan, prescan, review, translate, then the lexicon's scan, model
# scan, review and suggest) through GlossaryService and LexiconService on the nine committed invented books
# (BOOKLOOM_EVAL_BOOK=<id>|all) and, when BOOKLOOM_CORPUS_DIR is set, on every <name>.gold.json there (reports end in
# -names.json). One row per model and book: gender % over the characters with five or more mentions, wrong-but-verified
# genders, type %, the share of person clusters with one gender, spellings per cluster, junk kept, real names lost,
# recurring terms right, calls and minutes. The per-model timeout defaults to 5400 s, as for sequence.
# --suite coref (15i.E2 with 15i.N4) sends each invented coreference window (eval/coref/windows.json) as one term-reading
# call and scores the gender votes the reading keeps (reports end in -coref.json): one row per model with the windows
# read right, the wrong votes, the abstentions and the wrong-vote rate (the gate is at most 5 %, else that model's votes
# only break ties).
# --suite mini (15j.J15) runs the invented 28-block EPUB (eval/mini/book.json, one block per defect seen so far) through the
# names pass, the translation job and the export's consistency pass, and scores each expectation of eval/mini/expectations.json
# (reports end in -mini-<policy>.json): one row per model with the expectations met, the segments flagged, the calls and the
# seconds, then the defects that missed. Measured: see docs/DEVELOPMENT.md. --names transliterate|translate|keep_original (default
# transliterate, BOOKLOOM_EVAL_NAMES) is the brief's name policy and decides what the three brand expectations demand;
# BOOKLOOM_EVAL_DIAL and BOOKLOOM_EVAL_WINDOW pass through.
# --batch-format (15i.X1, an experiment) chooses how a batch is sent and answered: json = numbered items and a JSON reply (the
# default), markers = one passage marked ⟦s1⟧ … with a plain-text reply and no schema, dual = the passage as context beside
# the numbered items with a JSON reply. It reaches the code as BOOKLOOM_EVAL_BATCH_FORMAT and each report's provenance names it.
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
NAMES_POLICY=transliterate
NARRATOR=unset
NARRATOR_GENDER=male
RELEVANT_PAIRS=""
BATCH_FORMAT=
REASONING=""
BATCH_NOTES=""
DEFAULT_CONTEXT=16384
# An entry is model,context,label: the columns of a line of eval-models.txt joined by commas ("-" for no context).
entries() { awk '{ print $1 "," ($2 == "" ? "-" : $2) "," $3 }' | tr '\n' ' '; }
# The default is the first model of the list (the e4b target); --all-models restores every line, --models picks entries.
ALL_MODELS=$(grep -vE '^\s*(#|$)' scripts/eval-models.txt | entries)
MODELS=$(grep -vE '^\s*(#|$)' scripts/eval-models.txt | head -n 1 | entries)
QUICK=0
BOOK_FILE=""
OUT_DIR=""

while [ $# -gt 0 ]; do
  case "$1" in
    --stability) STABILITY=$2; shift 2 ;;
    --only) ONLY=$2; shift 2 ;;
    --models) MODELS=$(echo "$2" | tr ' ' '\n' | sed 's/,/ /g' | entries); shift 2 ;;
    --all-models) MODELS=$ALL_MODELS; shift ;;
    --quick) QUICK=1; shift ;;
    --book-file) BOOK_FILE=$2; shift 2 ;;
    --out-dir) OUT_DIR=$2; shift 2 ;;
    --table-only) TABLE_ONLY=1; shift ;;
    --rules) RULES=$2; shift 2 ;;
    --langs) LANGS=$2; shift 2 ;;
    --suite) SUITE=$2; shift 2 ;;
    --batch-sizes) BATCH_SIZES=$2; shift 2 ;;
    --narrator) NARRATOR=$2; shift 2 ;;
    --names) NAMES_POLICY=$2; shift 2 ;;
    --narrator-gender) NARRATOR_GENDER=$2; shift 2 ;;
    --relevant-pairs) RELEVANT_PAIRS=$2; shift 2 ;;
    --batch-format)
      case "$2" in json|markers|dual) BATCH_FORMAT=$2 ;; *) echo "--batch-format takes json, markers or dual" >&2; exit 2 ;; esac
      shift 2 ;;
    --reasoning) REASONING=$2; shift 2 ;;
    --batch-notes) BATCH_NOTES=$2; shift 2 ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done

if [ "$QUICK" = 1 ]; then
  export BOOKLOOM_EVAL_QUICK=1
  if [ "$SUITE" = sequence ]; then
    echo "-- --quick: the sequence suite is replaced by mini (the invented 28-block book)"
    SUITE=mini
  fi
  if [ "$SUITE" = names ] && [ -z "${BOOKLOOM_EVAL_BOOK:-}" ]; then export BOOKLOOM_EVAL_BOOK=mayday; fi
fi
if [ -n "$BOOK_FILE" ]; then
  [ -f "$BOOK_FILE" ] || { echo "--book-file: no such file $BOOK_FILE" >&2; exit 2; }
  BOOK_FILE=$(cd "$(dirname "$BOOK_FILE")" && pwd)/$(basename "$BOOK_FILE")
  CORPUS_TMP=$(mktemp -d)
  trap 'rm -rf "$CORPUS_TMP"' EXIT
  ln -s "$BOOK_FILE" "$CORPUS_TMP/$(basename "$BOOK_FILE")"
  gold="${BOOK_FILE%.*}.gold.json"
  # The corpus names a book corpus-<id>.epub and its gold <id>.gold.json.
  [ -f "$gold" ] || gold="$(dirname "$BOOK_FILE")/$(basename "${BOOK_FILE%.*}" | sed 's/^corpus-//').gold.json"
  [ -f "$gold" ] && ln -s "$gold" "$CORPUS_TMP/$(basename "$gold")"
  export BOOKLOOM_CORPUS_DIR=$CORPUS_TMP
  if [ "$SUITE" = book ]; then export BOOKLOOM_EVAL_BOOK_FILE=$BOOK_FILE; fi
  echo "-- --book-file: BOOKLOOM_CORPUS_DIR=$CORPUS_TMP holds $(basename "$BOOK_FILE")"
fi

if [ "$SUITE" = book ]; then
  { [ -n "$BOOK_FILE" ] && [ -n "$OUT_DIR" ]; } || { echo "--suite book needs --book-file and --out-dir" >&2; exit 2; }
  mkdir -p "$OUT_DIR"
  export BOOKLOOM_EVAL_OUT_DIR=$(cd "$OUT_DIR" && pwd)
  MATRIX_TESTS='*BookRunEvalTest'
fi
export MATRIX_TESTS="${MATRIX_TESTS:-}"

REPORTS=modules/pipeline/build/reports/promptEval
if [ "$SUITE" = book ]; then DEFAULT_TIMEOUT=86400; elif [ "$SUITE" = sequence ] || [ "$SUITE" = names ]; then DEFAULT_TIMEOUT=5400; else DEFAULT_TIMEOUT=1500; fi

# One (suite x model) Gradle run with its watchdog; it runs under scripts/gradle-lock.sh (see gradle_locked), so the
# timeout counts only the time Gradle really holds the lock, not the wait in the queue.
gradle_section() {
  ./gradlew :pipeline:promptEval ${MATRIX_TESTS:+--tests "$MATRIX_TESTS"} >"$MATRIX_LOG" 2>&1 &
  local pid=$!
  # The watchdog holds its own sleep, so ending it must end the sleep too: an orphaned sleep keeps the script's stdout
  # pipe open until the timeout runs out when the output is piped.
  ( sleep "${MODEL_TIMEOUT:-$DEFAULT_TIMEOUT}" & sleeper=$!
    trap 'kill "$sleeper" 2>/dev/null; exit 0' TERM
    wait "$sleeper" && { pkill -P "$pid" 2>/dev/null; kill "$pid" 2>/dev/null; pkill -f "Gradle Test Executor" 2>/dev/null; } ) &
  local dog=$!
  local status=0
  wait "$pid" || status=$?
  kill "$dog" 2>/dev/null
  wait "$dog" 2>/dev/null
  return "$status"
}
export -f gradle_section

# Takes the lock for the one Gradle invocation named by the caller's environment and nothing else.
gradle_locked() { scripts/gradle-lock.sh bash -c gradle_section; }

# run_one <entry> [narrator] [keep]: keep=1 leaves an Ollama model loaded because the next run uses it too.
run_one() {
  local entry=$1 narrator=${2:-unset} keep=${3:-0} spec context label provider model api_model url env_provider log
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
  if ! MATRIX_LOG="$log" DEFAULT_TIMEOUT=$DEFAULT_TIMEOUT MODEL_TIMEOUT=${MODEL_TIMEOUT:-} \
    BOOKLOOM_EVAL_URL=$url BOOKLOOM_EVAL_PROVIDER=$env_provider BOOKLOOM_EVAL_MODEL=$api_model \
    BOOKLOOM_EVAL_ONLY=$ONLY BOOKLOOM_EVAL_STABILITY=$STABILITY BOOKLOOM_EVAL_RULES=$RULES BOOKLOOM_EVAL_LANGS=$LANGS BOOKLOOM_EVAL_SUITE=$SUITE BOOKLOOM_EVAL_NARRATOR=$narrator BOOKLOOM_EVAL_NARRATOR_GENDER=$NARRATOR_GENDER BOOKLOOM_EVAL_BATCH_SIZES=$BATCH_SIZES BOOKLOOM_EVAL_NAMES=$NAMES_POLICY BOOKLOOM_EVAL_RELEVANT_PAIRS=$RELEVANT_PAIRS BOOKLOOM_EVAL_REASONING=$REASONING BOOKLOOM_EVAL_BATCH_NOTES=$BATCH_NOTES BOOKLOOM_EVAL_BATCH_FORMAT=$BATCH_FORMAT \
    gradle_locked; then
    echo "   !! gradle failed, a threshold was missed, or the run timed out; what $log says:"
    grep -E "Eval window|FAILED|AssertionError|Exception|Expecting" "$log" | head -15 | sed 's/^/   | /'
    tail -5 "$log" | sed 's/^/   | /'
  fi
  if [ "$provider" = lmstudio ]; then lms unload --all >/dev/null 2>&1
  elif [ "$keep" = 0 ]; then ollama stop "$model" >/dev/null 2>&1; fi
}

run_recall() {
  [ -n "${BOOKLOOM_RECALL_DIR:-}" ] || { echo "!! --suite recall needs BOOKLOOM_RECALL_DIR" >&2; exit 2; }
  mkdir -p build/eval-matrix
  local log=build/eval-matrix/recall.log
  echo "== recall dir=$BOOKLOOM_RECALL_DIR (gradle log: $log)"
  if ! BOOKLOOM_EVAL_SUITE=recall scripts/gradle-lock.sh ./gradlew :pipeline:promptEval --tests '*RecallEvalTest*' >"$log" 2>&1; then
    echo "   !! gradle failed; what $log says:"
    grep -E "FAILED|AssertionError|Exception|Expecting" "$log" | head -15 | sed 's/^/   | /'
  fi
}

if [ "$TABLE_ONLY" = 0 ] && [ "$SUITE" = recall ]; then
  run_recall
elif [ "$TABLE_ONLY" = 0 ]; then
  if [ "$NARRATOR" = both ]; then MODES="unset set"; else MODES=$NARRATOR; fi
  # The runs in order as "entry|mode" lines, so a run can tell whether the next one uses the same model: an Ollama model
  # is stopped only when the next run is a different model or there is none.
  RUNS=()
  for m in $MODELS; do
    if [ "$SUITE" = sequence ]; then for n in $MODES; do RUNS+=("$m|$n"); done; else RUNS+=("$m|unset"); fi
  done
  for i in "${!RUNS[@]}"; do
    entry=${RUNS[$i]%%|*}; mode=${RUNS[$i]##*|}
    keep=0
    if [ -n "${RUNS[$((i + 1))]:-}" ] && [ "${RUNS[$((i + 1))]%%|*}" = "$entry" ]; then keep=1; fi
    run_one "$entry" "$mode" "$keep"
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
    print("(+Nk: N known failures reported beside the rate, see additional-work.md 15e.2)")
    sys.exit(0)
if sys.argv[2] == "sequence":
    runs = sorted((r for r in reports if r.get("suite") == "sequence"), key=lambda r: (r.get("model", ""), r.get("narrator", "")))
    print("%-30s %-6s %-8s %5s %7s %6s %7s %7s %6s %6s %6s %6s %7s %7s %6s %7s" % ("model", "narr", "dial", "segs", "rend/tm", "nameV", "gender", "english", "quote", "ascii", "mixed", "flag%", "noTgt", "fallbk%", "calls", "sec/seg"))
    for r in runs:
        g = lambda k, r=r: r.get(k, 0)
        print("%-30s %-6s %-8s %5d %7.2f %6d %7d %7d %6d %6d %6d %5.1f%% %7d %6.1f%% %6.2f %7.2f" % (r.get("model", "?"), r.get("narrator", "?"), r.get("dial", "?"), g("segments"), g("renderingsPerTerm"), g("nameVariants"), g("genderSlips"), g("englishLeftovers"), g("quoteFailures"), g("asciiQuotes"), g("mixedScript"), 100.0 * g("flagged") / max(1, g("segments")), g("flaggedWithoutTarget"), 100 * g("batchFallbackRate"), g("callsPerSegment"), g("secondsPerSegment")))
    print()
    print("%-30s %-6s %7s %7s %8s %8s %8s %8s %8s %8s  %s" % ("model", "narr", "hard0", "leaked", "revTrunc", "claimed", "domin%", "learned", "cover", "LTCR-1", "per term: distinct renderings (dominant share)"))
    for r in runs:
        print("%-30s %-6s %7d %7d %8d %8d %7.0f%% %8d %7.0f%% %7.1f%%  %s" % (r.get("model", "?"), r.get("narrator", "?"), r.get("hardGateFailuresRound0", 0), r.get("leakedProtocol", 0), r.get("reviewerTruncated", 0), r.get("termClaimedWrong", 0), 100 * r.get("dominantShareMean", 0), r.get("learned", 0), 100 * r.get("learnedCoverage", 0), 100 * r.get("ltcr1", 0), " ".join("%s=%d(%.0f%%)" % (t.get("term", "?"), t.get("distinct", 0), 100 * t.get("dominantShare", 0)) for t in r.get("terms", []))))
    print("(rend/tm: mean distinct renderings per fixture term, 1.00 is ideal; gender: narrator slips in the first-person chapters; hard0: segments whose first round failed a hard gate; claimed: terms sharing a learned rendering; LTCR-1: the share of a term's or name's later occurrences that carry the rendering its first one had, DelTA's consistency metric)")
    sys.exit(0)
if sys.argv[2] == "names":
    print("%-30s %-32s %7s %6s %6s %9s %6s %5s %5s %9s %5s %6s %7s" % ("model", "book", "gender", "wrongV", "type", "cluster1G", "spell", "junk", "lost", "termsOK", "promo", "calls", "minutes"))
    for r in (r for r in reports if r.get("suite") == "names"):
        for b in r["books"]:
            s = b.get("score")
            if s is None:
                print("%-30s %-32s error %s" % (r["model"][-30:], b["book"][-32:], b.get("error")))
                continue
            print("%-30s %-32s %6.0f%% %6d %5.0f%% %8.0f%% %6d %5d %5d %4d/%-4d %5d %6d %7.1f" % (r["model"][-30:], b["book"][-32:], 100 * s["genderAccuracy"], s["wrongButVerified"], 100 * s["typeAccuracy"], 100 * s["clusterRate"], max(s["spellings"].values(), default=0), s["junkKept"], s["realNamesLost"], s["recurringKept"], s["recurringKept"] + s["recurringDropped"], s.get("recurringPromoted", 0), s["calls"], s["minutes"]))
    print("(gender: share of characters with five or more mentions that ended with the gold gender; wrongV: a wrong gender not marked suggested; cluster1G: person clusters holding one gender; spell: most spellings of one person's name, 1 is ideal; junk: gold junk left in the glossary; lost: real names missing; termsOK: gold recurring terms kept of those to keep; promo: of the ones not kept, how many became glossary entries)")
    sys.exit(0)
if sys.argv[2] == "book":
    print("book run finished; its outputs are in the --out-dir")
    sys.exit(0)
if sys.argv[2] == "mini":
    runs = sorted((r for r in reports if r.get("suite") == "mini"), key=lambda r: (r.get("model", ""), r.get("names", "")))
    print("%-30s %-14s %-8s %7s %6s %5s %6s %6s %6s %s" % ("model", "names", "dial", "met", "segs", "flag", "calls", "sec", "job", "misses"))
    for r in runs:
        miss = [x["id"] for x in r.get("results", []) if not x["passed"]]
        print("%-30s %-14s %-8s %3d/%-3d %6d %5d %6d %6.0f %6s %s" % (r.get("model", "?")[-30:], r.get("names", "?"), r.get("dial", "?"), r.get("passed", 0), r.get("expectations", 0), r.get("segments", 0), r.get("flagged", 0), r.get("calls", 0), r.get("seconds", 0), r.get("end", "?"), " ".join(miss)))
    print()
    print("%-30s %-14s %s" % ("model", "names", "per defect: met/total"))
    for r in runs:
        print("%-30s %-14s %s" % (r.get("model", "?")[-30:], r.get("names", "?"), " ".join("%s=%d/%d" % (d, c["passed"], c["total"]) for d, c in r.get("byDefect", {}).items())))
    print("(met: expectations of the book met / asked at this name policy; misses: the ids not met, their finding is in the report's txt; a brand expectation depends on BOOKLOOM_EVAL_NAMES)")
    sys.exit(0)
if sys.argv[2] == "coref":
    print("%-36s %7s %6s %6s %8s %9s" % ("model", "windows", "right", "wrong", "abstain", "wrongVote"))
    for r in (r for r in reports if r.get("suite") == "coref"):
        print("%-36s %7d %6d %6d %8d %8.1f%%" % (r["model"], r["windows"], r["right"], r["wrong"], r["abstain"], 100 * r["wrongVoteRate"]))
    print("(wrongVote: wrong votes / (right + wrong votes); an abstention is neither. The 15i.N4 gate: at most 5 %, else that model's votes only break ties)")
    sys.exit(0)
if sys.argv[2] == "recall":
    pct = lambda v: "-" if v is None else "%.0f%%" % (100 * v)
    for r in (r for r in reports if r.get("suite") == "recall"):
        print("books=%d aligned=%d read=%d defective=%d unaligned=%d staleGold=%d recall=%s falseAlarm=%s" % (len(r["books"]), r["aligned"], r["read"], r["defective"], r["unaligned"], r["staleGold"], pct(r["recall"]), pct(r["falseAlarm"])))
        print("%-32s %6s %6s %9s %8s %6s" % ("detector", "fired", "tp", "precision", "expected", "recall"))
        for d in r["detectors"]:
            print("%-32s %6d %6d %9s %8d %6s" % (d["name"], d["fired"], d["truePositive"], pct(d["precision"]), d["expected"], pct(d["recall"])))
        print("%-32s %6s %6s %6s %7s %9s" % ("class", "segs", "caught", "recall", "flagged", "precision"))
        for c in r["classes"]:
            print("%-32s %6d %6d %6s %7d %9s" % (c["name"], c["segments"], c["caught"], pct(c["recall"]), c["flagged"], pct(c["precision"])))
    sys.exit(0)
if sys.argv[2] in ("stability", "gold"):
    runs = [r for r in reports if r.get("suite") == sys.argv[2]]
    print("%-36s %5s %5s %8s %8s %6s %s" % ("model", "books", "items", "agreeMin", "accuracy", "below", "seeds"))
    for r in runs:
        print("%-36s %5d %5d %7.0f%% %7.0f%% %6d %s" % (r["model"], len(r["books"]), sum(len(b["items"]) for b in r["books"]), 100 * r["agreement"], 100 * r["accuracy"], len(r["belowTarget"]), ",".join(str(s) for s in r["seeds"])))
    keys, answers = [], {}
    for r in runs:
        for b in r["books"]:
            for i in b["items"]:
                key = b["book"] + ":" + i["name"]
                if key not in keys:
                    keys.append(key)
                answers.setdefault(key, {})[r["model"]] = i
    print()
    print("%-40s %-10s " % ("book:field (agreement/accuracy)", "gold") + " ".join("%11s" % r["model"][-11:] for r in runs) + "  all-models")
    for key in keys:
        cells = []
        pooled = []
        for r in runs:
            i = answers[key].get(r["model"])
            cells.append("%11s" % ("-" if i is None else "%3.0f/%3.0f%%" % (100 * i["agreement"], 100 * i["accuracy"])))
            pooled += [] if i is None else i["answers"]
        most = max((pooled.count(a) for a in set(pooled)), default=0)
        gold = next(iter(answers[key].values()))["gold"]
        print("%-40s %-10s " % (key[-40:], gold[:10]) + " ".join(cells) + "  %5.0f%%" % (100.0 * most / max(1, len(pooled))))
    print("(agreement: share of a field's answers across seeds that are the most common one, target %.0f%%; accuracy: share that match the gold; all-models: agreement of every model's answers pooled)" % (100 * (runs[0]["target"] if runs else 0.8)))
    sys.exit(0)
if sys.argv[2] == "words":
    print("%-36s %5s %8s %8s %6s" % ("model", "cases", "recall", "falsePos", "ok"))
    for r in (r for r in reports if r.get("suite") == "words"):
        print("%-36s %5d %7.0f%% %7.0f%% %6s" % (r["model"], r["cases"], 100 * r["recall"], 100 * r["falsePositive"], "yes" if r["meetsTarget"] else "NO"))
    sys.exit(0)
STAGES = ("prescan", "terms", "setup", "consistency", "retry", "replay")
if sys.argv[2] in STAGES:
    print("%-36s %-12s %5s %6s %6s %6s %8s %7s" % ("model", "suite", "cases", "pass", "calls", "failed", "repeated", "refused"))
    for r in (r for r in reports if r.get("suite") == sys.argv[2]):
        print("%-36s %-12s %5d %5.0f%% %6d %6d %8d %7d" % (r["model"], r["suite"], r["cases"], 100 * r["passRate"], r["calls"], r["failed"], r["repeated"], r["refused"]))
    fields = [r for r in reports if r.get("suite") == "setup" == sys.argv[2] and "perField" in r]
    if fields:
        names = list(fields[0]["perField"])
        print()
        print("%-36s " % "setup right per field" + " ".join("%s" % n for n in names))
        for r in fields:
            print("%-36s " % r["model"] + " ".join("%*s" % (len(n), "n/a" if r["perField"].get(n) is None else "%.0f%%" % (100 * r["perField"][n])) for n in names))
    sys.exit(0)
rows = [r for r in reports if r.get("suite") not in ("batch", "words", "realrun", "sequence", "recall", "stability", "gold", "names", "coref", "mini", "book") + STAGES]
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
