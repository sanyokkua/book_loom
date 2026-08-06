#!/usr/bin/env sh
#
# Check: staging a mis-formatted Java file and committing it produces a COMMITTED BLOB that is formatted.
#
# This is the claim `stage_fixed: true` actually makes, and the one worth checking: it is not enough that
# Spotless rewrote the file on disk — the rewritten content has to make it back into the index before git
# builds the commit object. A hook that formats the working tree but commits the original is the exact silent
# failure this check exists to catch, and it looks completely green from the outside.
#
# Run from the repository root, with hooks installed:  sh tooling/hooks-test/pre-commit-formats-staged-file.sh

. tooling/hooks-test/lib.sh

hooks_test_begin 'pre-commit formats the staged file'

HOOKS_TEST_FIXTURE='modules/util/src/main/java/ua/bookloom/util/paths/HookFormatProbe.java'

# Deliberately mangled in ways Palantir Java Format is guaranteed to correct: a 12-space indent, trailing
# whitespace, a doubled blank line, and no final newline. The trailing space is load-bearing —
# `trimTrailingWhitespace()` removes it, giving a byte-level difference that cannot be argued with.
printf '%s' 'package ua.bookloom.util.paths;


/** Throwaway fixture written by tooling/hooks-test — never committed to a real branch. */
public interface HookFormatProbe {
            void probe();
}' > "$HOOKS_TEST_FIXTURE"

staged_before=$(cat "$HOOKS_TEST_FIXTURE")

git add -- "$HOOKS_TEST_FIXTURE"
git commit -q -m 'test(hooks): probe that pre-commit re-stages formatted output'

committed=$(git show "HEAD:$HOOKS_TEST_FIXTURE")

# 1. The hook changed what got committed at all.
if [ "$committed" = "$staged_before" ]; then
    fail 'the committed blob is byte-identical to the mis-formatted file that was staged — stage_fixed did not re-stage'
fi
pass 'the committed blob differs from the mis-formatted content that was staged'

# 2. What was committed is what Spotless produced, not some third thing.
if [ "$committed" != "$(cat "$HOOKS_TEST_FIXTURE")" ]; then
    fail 'the committed blob differs from the formatted file on disk'
fi
pass 'the committed blob matches the Spotless-formatted file on disk'

# 3. Concretely formatted, checked against each defect that was seeded.
if printf '%s\n' "$committed" | grep -q '[[:space:]]$'; then
    fail 'the committed blob still has trailing whitespace'
fi
pass 'no trailing whitespace in the committed blob'

if printf '%s\n' "$committed" | grep -q '^            void probe();$'; then
    fail 'the committed blob still has the seeded 12-space indent'
fi
if ! printf '%s\n' "$committed" | grep -q '^    void probe();$'; then
    fail 'the committed blob does not have the expected 4-space indent'
fi
pass 'indentation was normalised to 4 spaces'

# 4. And the authority agrees: Spotless itself considers the committed content clean. Run over the whole
#    project, which is only meaningful because the tree is otherwise formatted — a failure here means either
#    the hook or some unrelated file, so the log is printed rather than summarised.
git show "HEAD:$HOOKS_TEST_FIXTURE" > "$HOOKS_TEST_TMP/committed.java"
cp "$HOOKS_TEST_TMP/committed.java" "$HOOKS_TEST_FIXTURE"
if ! ./gradlew -q spotlessCheck > "$HOOKS_TEST_TMP/spotless.log" 2>&1; then
    cat "$HOOKS_TEST_TMP/spotless.log" >&2
    fail 'spotlessCheck rejects the committed content (see the log above)'
fi
pass 'spotlessCheck passes on the committed content'

printf '\nPASS: pre-commit formats the staged file and commits the formatted bytes.\n'
