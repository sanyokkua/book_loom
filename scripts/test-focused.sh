#!/usr/bin/env bash
#
# The focused test loop: format and test only what the working tree changed, plus the modules that depend on it.
#
# What it runs, from `git diff --name-only <base>` plus untracked files:
#   * `:<m>:spotlessApply` for every changed module;
#   * for each CHANGED module, `:<m>:test --tests <class>` for the test classes the diff points at — a changed test
#     class, or `<Name>*Test` next to a changed `<Name>.java` — and `:<m>:fastTest` when nothing can be pointed at
#     (a changed resource, a test helper, a class with no test of its own);
#   * `:<d>:fastTest` for every module that depends on a changed one (the module graph below).
# A change to the build itself (`modules/build-logic`, `gradle/`, root `*.gradle.kts`) counts as every module.
# `fastTest` is everything `test` runs except the `slow`-tagged end-to-end classes.
#
# This is the inner loop, never the evidence a change is done: run the module's `check` once before committing
# (`--full-module`), and the whole gate `./gradlew clean build check spotlessCheck` at the end of a feature.
#
# Usage: scripts/test-focused.sh [options] [module...]
#   module...          test these modules instead of the ones the diff names (api util document llm persistence
#                      pipeline ui app)
#   --tests <pattern>  run only this test pattern in the changed modules (repeatable; Gradle `--tests` syntax)
#   --fast             run `fastTest` for the changed modules instead of the classes the diff points at
#   --full-module      run `check` (every test incl. `slow`, plus lint) for the changed modules
#   --no-dependents    do not test the modules that depend on the changed ones
#   --base <ref>       diff against <ref> instead of HEAD (e.g. the feature branch's merge base)
#   --dry-run          print the Gradle command and exit
#   --lines <n>        how many lines of Gradle output to show at the end (default 40; the full log is kept)
#
# Exit status is Gradle's. The full output is written to build/test-focused.log.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_FILE="${REPO_ROOT}/build/test-focused.log"
ALL_MODULES="api util document llm persistence pipeline ui app"

# The modules that depend on each module, directly or not — the reverse of the project dependencies in
# modules/*/build.gradle.kts (docs/implementation_plan/01_MODULE_INVENTORY.md). Kept as a table, not derived, so the
# script needs no Gradle run to plan one.
dependents_of() {
    case "$1" in
        api) echo "util document llm persistence pipeline ui app" ;;
        util) echo "document llm persistence pipeline ui app" ;;
        document | llm | persistence) echo "pipeline ui app" ;;
        pipeline) echo "ui app" ;;
        ui) echo "app" ;;
        *) echo "" ;;
    esac
}

usage() {
    sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

contains() { # contains <word> <list...>
    local word="$1"
    shift
    local item
    for item in "$@"; do
        [[ "$item" == "$word" ]] && return 0
    done
    return 1
}

base="HEAD"
mode="classes"
with_dependents=1
dry_run=0
tail_lines=40
explicit_modules=()
explicit_tests=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tests) explicit_tests+=("$2"); shift 2 ;;
        --fast) mode="fast"; shift ;;
        --full-module) mode="check"; shift ;;
        --no-dependents) with_dependents=0; shift ;;
        --base) base="$2"; shift 2 ;;
        --dry-run) dry_run=1; shift ;;
        --lines) tail_lines="$2"; shift 2 ;;
        -h | --help) usage 0 ;;
        -*) echo "unknown option: $1" >&2; usage 2 ;;
        *)
            contains "$1" $ALL_MODULES || { echo "unknown module: $1" >&2; exit 2; }
            explicit_modules+=("$1"); shift ;;
    esac
done

cd "$REPO_ROOT"

changed_files="$( (git diff --name-only "$base"; git ls-files --others --exclude-standard) | sort -u)"

# --- Which modules changed --------------------------------------------------------------------------------------
changed_modules=()
if [[ ${#explicit_modules[@]} -gt 0 ]]; then
    changed_modules=("${explicit_modules[@]}")
else
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        case "$file" in
            modules/build-logic/* | gradle/* | build.gradle.kts | settings.gradle.kts)
                # shellcheck disable=SC2206
                changed_modules=($ALL_MODULES)
                break ;;
            modules/*/*)
                module="${file#modules/}"
                module="${module%%/*}"
                if contains "$module" $ALL_MODULES && ! contains "$module" "${changed_modules[@]+"${changed_modules[@]}"}"; then
                    changed_modules+=("$module")
                fi ;;
        esac
    done <<< "$changed_files"
fi

if [[ ${#changed_modules[@]} -eq 0 ]]; then
    echo "test-focused: no module changed against $base — nothing to test."
    exit 0
fi

# --- The test classes a module's diff points at -----------------------------------------------------------------
# An abstract contract test runs only through its subclasses, and a local-only class never runs in `test`; neither
# can be named to `--tests` on its own.
runnable_test() {
    ! grep -qE '^(public )?abstract class|^@Tag\("(liveLocal|promptEval|visual|corpus)"\)' "$1"
}

# Prints one fully-qualified class per line, or nothing when some changed file maps to no test (then the caller
# falls back to the whole fast set, because a narrower run would silently skip what the change can break).
classes_for() {
    local module="$1" file rel name found any_unmapped=0
    local test_root="modules/${module}/src/test/java/"
    local main_root="modules/${module}/src/main/java/"
    local classes=""
    while IFS= read -r file; do
        [[ "$file" == modules/${module}/* ]] || continue
        case "$file" in
            "$test_root"*Test.java)
                [[ -f "$file" ]] || continue
                if runnable_test "$file"; then
                    rel="${file#"$test_root"}"
                    classes+="${rel%.java}"$'\n'
                else
                    any_unmapped=1
                fi ;;
            "$main_root"*.java)
                [[ -f "$file" ]] || continue
                name="$(basename "$file" .java)"
                [[ "$name" == "package-info" || "$name" == "module-info" ]] && continue
                found="$(find "$test_root" -name "${name}*Test.java" 2>/dev/null || true)"
                if [[ -z "$found" ]]; then
                    any_unmapped=1
                else
                    while IFS= read -r rel; do
                        runnable_test "$rel" || continue
                        rel="${rel#"$test_root"}"
                        classes+="${rel%.java}"$'\n'
                    done <<< "$found"
                fi ;;
            *) any_unmapped=1 ;;
        esac
    done <<< "$changed_files"
    if [[ $any_unmapped -eq 0 && -n "$classes" ]]; then
        printf '%s' "$classes" | tr '/' '.' | sort -u
    fi
}

# --- The Gradle command ------------------------------------------------------------------------------------------
tasks=()
for module in "${changed_modules[@]}"; do
    tasks+=(":${module}:spotlessApply")
done

described=()
for module in "${changed_modules[@]}"; do
    if [[ "$mode" == "check" ]]; then
        tasks+=(":${module}:check")
        described+=("${module}: check")
    elif [[ ${#explicit_tests[@]} -gt 0 ]]; then
        tasks+=(":${module}:test")
        for pattern in "${explicit_tests[@]}"; do tasks+=(--tests "$pattern"); done
        described+=("${module}: test --tests ${explicit_tests[*]}")
    else
        classes=""
        [[ "$mode" == "classes" ]] && classes="$(classes_for "$module")"
        if [[ -n "$classes" ]]; then
            tasks+=(":${module}:test")
            count=0
            while IFS= read -r class; do
                tasks+=(--tests "$class")
                count=$((count + 1))
            done <<< "$classes"
            described+=("${module}: test, ${count} class(es) the diff points at")
        else
            tasks+=(":${module}:fastTest")
            described+=("${module}: fastTest")
        fi
    fi
done

if [[ $with_dependents -eq 1 ]]; then
    for module in "${changed_modules[@]}"; do
        for dependent in $(dependents_of "$module"); do
            contains "$dependent" "${changed_modules[@]}" && continue
            contains ":${dependent}:fastTest" "${tasks[@]}" && continue
            tasks+=(":${dependent}:fastTest")
            described+=("${dependent}: fastTest (depends on ${module})")
        done
    done
fi

echo "test-focused: against ${base}"
printf '  %s\n' "${described[@]}"
command=(./gradlew --continue --console=plain "${tasks[@]}")
if [[ $dry_run -eq 1 ]]; then
    printf '%q ' "${command[@]}"
    echo
    exit 0
fi

mkdir -p "$(dirname "$LOG_FILE")"
start=$(date +%s)
status=0
"${command[@]}" > "$LOG_FILE" 2>&1 || status=$?
elapsed=$(($(date +%s) - start))

grep -E "FAILED$|^[A-Za-z].*Test > .* FAILED" "$LOG_FILE" | sort -u | head -50 || true
echo "----- last ${tail_lines} lines (full log: ${LOG_FILE#"$REPO_ROOT"/}) -----"
grep -v -E "^WARNING: |^$" "$LOG_FILE" | tail -n "$tail_lines"
echo "test-focused: exit ${status} in ${elapsed}s"
exit "$status"
