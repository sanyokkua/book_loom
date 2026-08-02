#!/usr/bin/env bash
#
# fr-coverage.sh — which frozen requirement ids does the built system not claim yet?
#
# ADR-0016 (R6) replaced the generated `docs/traceability.yaml` and its Gradle validation gate
# with this grep. It answers one question, in two scopes:
#
#   repo scope  (default)  Of the FR-*/NFR-*/EC-* ids in the frozen spec, which are not cited by
#                          any requirement that has actually shipped into openspec/specs/?
#
#   change scope (--change) Of the ids in ONE area of the frozen spec, which does a given
#                          in-flight change claim, and which did it leave behind?
#
# The change scope is the important one. Repo scope tells you how much of the catalog is real —
# useful at a stage boundary, useless during a change. Change scope catches the failure mode that
# kills spec-driven workflows: a change that looks complete because every task is checked, while
# three edge cases from its own area were never turned into requirements at all. Run it during
# review, BEFORE /opsx:apply, when adding a missing requirement still costs nothing.
#
# Three id families are tracked, because all three are things the build must honour:
#   FR-*   functional requirements  (01_Product/01_FUNCTIONAL_REQUIREMENTS.md)
#   NFR-*  non-functional           (03_NonFunctional/**)
#   EC-*   enumerated edge cases    (scattered across ~12 spec files — no central catalog)
#
# `openspec/specs/` is the ledger of what is BUILT (each archived change folds its requirements
# in). `docs/specification/` is the catalog of what is INTENDED. A gap in repo scope is normal
# during build-out. A gap in change scope, for the area the change owns, is a defect.
#
# This is ADVISORY. Not a build gate, exits 0 either way, nothing in CI calls it.
#
# Usage:
#   bash scripts/fr-coverage.sh                     # repo scope, uncovered ids
#   bash scripts/fr-coverage.sh --covered           # repo scope, covered ids
#   bash scripts/fr-coverage.sh --summary           # counts only
#   bash scripts/fr-coverage.sh --change <name>     # what one in-flight change claims vs its area
#   bash scripts/fr-coverage.sh --area fr-doc       # every id defined under one spec area anchor

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
spec_dir="$repo_root/docs/specification"
catalog="$spec_dir/01_Product/01_FUNCTIONAL_REQUIREMENTS.md"
specs_dir="$repo_root/openspec/specs"
changes_dir="$repo_root/openspec/changes"

# ADR-0018 rules, all four load-bearing:
#   (^|[^A-Z0-9])  anchor the prefix — without it, `NFR-PRIV-01` yields the phantom id `FR-PRIV-01`
#   [A-Z0-9]+      digits allowed in the area — `A11Y`, `I18N`
#   [0-9]+[a-z]?   capture the sub-clause suffix — `FR-NOTIF-3a`, not a truncated `FR-NOTIF-3`
# Normalization to the canonical zero-padded form happens in norm() below.
ID_RE='(^|[^A-Z0-9])(FR|NFR|EC)-[A-Z0-9]+-[0-9]+[a-z]?'

if [[ ! -d "$spec_dir" ]]; then
  echo "error: frozen spec not found at $spec_dir" >&2
  exit 2
fi

# ---- helpers ---------------------------------------------------------------

# Extract every id from stdin, stripped of the anchor char and normalized to the ADR-0018
# canonical form: zero-padded to two digits, sub-clause suffix preserved.
#   FR-THEME-1  -> FR-THEME-01        FR-NOTIF-3a -> FR-NOTIF-03a
#   FR-DOC-01   -> FR-DOC-01          EC-DRM-1    -> EC-DRM-01
# Provably merges nothing: no id in the frozen spec exists in both padded and unpadded form
# (ADR-0018 §3). Its value is forward — a citation padded by analogy still joins.
norm() {
  grep -ohE "$ID_RE" | grep -ohE '(FR|NFR|EC)-[A-Z0-9]+-[0-9]+[a-z]?' |
    sed -E 's/-([0-9])([a-z]?)$/-0\1\2/' | sort -u
}

# Every requirement id defined anywhere in the frozen spec.
all_ids() { grep -rhoE "$ID_RE" "$spec_dir" | norm; }

# Ids cited by shipped requirements. openspec/specs/ starts empty and grows as changes are
# archived, so an absent or empty directory means "nothing built yet" — not an error.
shipped_ids() {
  [[ -d "$specs_dir" ]] && grep -rhoE "$ID_RE" "$specs_dir" 2>/dev/null | norm || true
}

# Ids cited by one in-flight change's delta specs.
change_ids() { grep -rhoE "$ID_RE" "$1/specs" 2>/dev/null | norm || true; }

# Ids defined under one area anchor. Catalog areas (fr-doc, fr-qa, …) resolve inside the FR
# catalog; the three area-doc families of ADR-0018 §6 resolve against their own home file.
area_ids() {
  local a="$1" src="$catalog"
  case "$a" in
    fr-theme) src="$spec_dir/01_Product/09_THEMING.md";              grep -hoE "$ID_RE" "$src" | norm | grep '^FR-THEME-'; return ;;
    fr-i18n)  src="$spec_dir/01_Product/10_I18N_AND_ACCESSIBILITY.md"; grep -hoE "$ID_RE" "$src" | norm | grep '^FR-I18N-';  return ;;
    fr-a11y)  echo "# FR-A11Y is advisory and never a merge gate — deliberately untracked (ADR-0018 §6)."
              echo "# Verify it as an advisory review item on :ui changes instead."
              exit 0 ;;
  esac
  awk -v a="{#$a}" '
    $0 ~ a          { on=1; next }
    on && /^## /    { exit }
    on              { print }
  ' "$src" | norm
}

count() { printf '%s\n' "${1:-}" | grep -c . || true; }

report() {
  local title="$1" ids="$2" empty="$3"
  echo "# $title"
  if [[ -n "$ids" ]]; then printf '%s\n' "$ids"; else echo "$empty"; fi
  echo
}

# ---- modes -----------------------------------------------------------------

mode="${1:---uncovered}"

case "$mode" in
  --change)
    name="${2:-}"
    [[ -n "$name" ]] || { echo "usage: --change <change-name>" >&2; exit 2; }
    root="$changes_dir/$name"
    [[ -d "$root" ]] || { echo "error: no change at $root" >&2; exit 2; }

    claimed="$(change_ids "$root")"

    if [[ -f "$root/.openspec.yaml" ]] && grep -q '^skip_specs: *true' "$root/.openspec.yaml"; then
      echo "# $name declares skip_specs: true — no requirements, nothing to claim."
      echo "# Verify instead that every task in tasks.md states its own check."
      exit 0
    fi

    report "Requirement ids claimed by $name" "$claimed" "(none — the change ships no delta spec)"

    # For each FR area the change touches, show what it did NOT claim. This is the number that
    # matters: it is the list of things the change is about to silently not implement.
    areas="$(printf '%s\n' "$claimed" | grep -oE '^FR-[A-Z0-9]+' | sed 's/^FR-/fr-/' | tr 'A-Z' 'a-z' | sort -u)"
    if [[ -n "$areas" ]]; then
      while read -r area; do
        [[ -n "$area" ]] || continue
        gap="$(comm -23 <(area_ids "$area") <(printf '%s\n' "$claimed" | grep -v '^$' || true))"
        report "Unclaimed in area $area — confirm each is deliberately out of scope" \
               "$gap" "(none — the area is fully claimed)"
      done <<< "$areas"
    fi
    exit 0
    ;;

  --area)
    area="${2:-}"
    [[ -n "$area" ]] || { echo "usage: --area <fr-doc|fr-qa|…>" >&2; exit 2; }
    report "Ids defined under $area" "$(area_ids "$area")" "(none — is the anchor spelled right?)"
    exit 0
    ;;
esac

# ---- repo scope ------------------------------------------------------------

# Both A11Y families are advisory and never a merge gate (ADR-0018 §6): 04_ACCESSIBILITY.md
# states "NFR-A11Y-* ids are retained for reference only and are advisory". Excluded so they
# cannot show as a permanent red line for work the specification deliberately declines to gate.
all="$(all_ids | grep -vE '^N?FR-A11Y-')"
cited="$(shipped_ids)"
uncovered="$(comm -23 <(printf '%s\n' "$all") <(printf '%s\n' "$cited" | grep -v '^$' || true))"
covered="$(comm -12 <(printf '%s\n' "$all") <(printf '%s\n' "$cited" | grep -v '^$' || true))"

case "$mode" in
  --summary) ;;
  --covered) report "Ids cited by a shipped requirement in openspec/specs/" "$covered" "(none)" ;;
  --uncovered|"") report "Ids in the frozen spec that no shipped requirement claims yet" \
                         "$uncovered" "(none — every catalog id is claimed)" ;;
  *) echo "usage: bash scripts/fr-coverage.sh [--uncovered|--covered|--summary|--change <name>|--area <anchor>]" >&2
     exit 2 ;;
esac

for fam in FR NFR EC; do
  t=$(printf '%s\n' "$all"     | grep -c "^$fam-" || true)
  c=$(printf '%s\n' "$covered" | grep -c "^$fam-" || true)
  printf '%-4s covered %3s/%-4s\n' "$fam" "$c" "$t"
done
echo "TOTAL covered $(count "$covered")/$(count "$all")  (advisory — never a build gate)"
