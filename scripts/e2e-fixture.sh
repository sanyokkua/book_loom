#!/usr/bin/env bash
#
# The headless end-to-end proof: translates the earth-gravity fixture with a real model through the command line,
# exactly as a window run would (brief, names review, outage recovery), then checks the written book with
# scripts/validate-translated-book.py, and prints one summary line per format.
#
# Usage: scripts/e2e-fixture.sh <epub|fb2|md|txt|all> [options]
#   --provider ollama|lmstudio    the provider preset (default ollama)
#   --model <id>                  the model (default gemma4:e4b-mlx for Ollama, google/gemma-4-e4b for LM Studio)
#   --quality fast|balanced|max   the quality dial (default balanced)
#   --names translate|transliterate|keep   the name policy (default transliterate)
#   --no-review-names             skip the names scan, review and accept-all before translating
#   --out <dir>                   where the books, reports and validator output go (default build/e2e)
#   -- <args...>                  anything after -- goes to the translate command as it is
#
# Per format it writes <out>/<fmt>/earth-gravity.uk.<fmt>, report.json (the command's --report), console.txt and
# validator.txt. The local model must be served already; nothing is started or stopped. Exit status: 0 when every
# format's validator passed, 1 otherwise.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURES="${REPO_ROOT}/modules/app/src/test/resources/fixtures/earth-gravity"

usage() {
    sed -n '7,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 2
}

[[ $# -ge 1 ]] || usage
WHICH="$1"
shift
PROVIDER="ollama"
MODEL=""
QUALITY="balanced"
NAMES="transliterate"
REVIEW_NAMES="--review-names"
OUT="${REPO_ROOT}/build/e2e"
EXTRA=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --provider) PROVIDER="$2"; shift 2 ;;
        --model) MODEL="$2"; shift 2 ;;
        --quality) QUALITY="$2"; shift 2 ;;
        --names) NAMES="$2"; shift 2 ;;
        --no-review-names) REVIEW_NAMES=""; shift ;;
        --out) OUT="$2"; shift 2 ;;
        --) shift; EXTRA=("$@"); break ;;
        *) echo "unknown option: $1" >&2; usage ;;
    esac
done
if [[ -z "${MODEL}" ]]; then
    case "${PROVIDER}" in
        lmstudio) MODEL="google/gemma-4-e4b" ;;
        *) MODEL="gemma4:e4b-mlx" ;;
    esac
fi
case "${WHICH}" in
    all) FORMATS=(epub fb2 md txt) ;;
    epub | fb2 | md | txt) FORMATS=("${WHICH}") ;;
    *) usage ;;
esac

# One summary line from the command's JSON report and the validator's RESULT line.
summarise() {
    python3 - "$@" <<'PY'
import json, sys, re
fmt, seconds, exit_code, report_path, validator_path = sys.argv[1:6]
try:
    r = json.load(open(report_path))
except Exception:
    r = {}
run = r.get("run", {}); exp = r.get("export", {})
segments = run.get("segments", 0) or 0
flagged = run.get("flagged", 0) or 0
pct = (100.0 * flagged / segments) if segments else 0.0
result = "no validator output"
fails = []
try:
    for line in open(validator_path):
        if line.startswith("RESULT:"):
            result = line.split("RESULT:", 1)[1].strip()
        elif line.startswith("FAIL"):
            fails.append(re.sub(r"\s+", " ", line.strip())[:110])
except FileNotFoundError:
    pass
print(f"{fmt:4} {float(seconds):6.0f}s exit={exit_code} segments={segments} accepted={run.get('accepted', '?')} "
      f"flagged={flagged} ({pct:.1f}%) auto={run.get('autoAccepted', '?')} repaired={run.get('repairedAccepted', '?')} "
      f"verbatim={run.get('keptVerbatim', '?')} pending={exp.get('pending', '?')} "
      f"fallbacks={len(exp.get('sourceFallbacks', []))} outages={len(r.get('recovery', {}).get('outages', []))} "
      f"| validator {result}")
for f in fails:
    print(f"       {f}")
PY
}

STATUS=0
for fmt in "${FORMATS[@]}"; do
    dir="${OUT}/${fmt}"
    mkdir -p "${dir}"
    cp "${FIXTURES}/earth-gravity.${fmt}" "${dir}/earth-gravity.${fmt}"
    out_book="${dir}/earth-gravity.uk.${fmt}"
    rm -f "${out_book}" "${dir}/report.json"
    args="'${dir}/earth-gravity.${fmt}' --to uk --from en --overwrite --provider ${PROVIDER} --model ${MODEL}"
    args+=" --quality ${QUALITY} --names ${NAMES} ${REVIEW_NAMES} --report '${dir}/report.json'"
    for extra in "${EXTRA[@]+"${EXTRA[@]}"}"; do args+=" '${extra}'"; done
    started=$(date +%s)
    (cd "${REPO_ROOT}" && ./gradlew -q :app:translate --args="${args}") >"${dir}/console.txt" 2>&1
    exit_code=$?
    seconds=$(( $(date +%s) - started ))
    if [[ -f "${out_book}" ]]; then
        python3 "${REPO_ROOT}/scripts/validate-translated-book.py" "${fmt}" "${FIXTURES}/earth-gravity.${fmt}" \
            "${out_book}" --lang uk >"${dir}/validator.txt" 2>&1
        grep -q "^RESULT: PASS" "${dir}/validator.txt" || STATUS=1
    else
        echo "no book written" >"${dir}/validator.txt"
        STATUS=1
    fi
    summarise "${fmt}" "${seconds}" "${exit_code}" "${dir}/report.json" "${dir}/validator.txt"
done
exit "${STATUS}"
