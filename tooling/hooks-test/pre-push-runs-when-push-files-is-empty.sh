#!/usr/bin/env sh
#
# Check: the pre-push gate RUNS even when lefthook computes an empty `{push_files}` list.
#
# This is a regression check for a real bug that was present in the first draft of lefthook.yml and found by
# accident. Written as a pre-push `jobs:` entry, the gate is filtered against `{push_files}` and lefthook
# silently SKIPS it when that list is empty — which it is when pushing an existing branch to a remote that does
# not yet have it. The observed behaviour was a gate that "passed" in 0.04 s and a ref that transferred anyway.
#
# The sibling check `pre-push-blocks-checkstyle-violation.sh` does NOT catch this: it pushes a branch carrying
# a brand-new commit, so `{push_files}` is non-empty and the job runs regardless. Only the no-new-commit push
# exercised here reaches the skip path — which is exactly why the bug survived the first round of checking.
#
# It also serves as the positive control for the gate as a whole: a green tree must be allowed through, or the
# other check would be satisfied by a hook that simply always fails.
#
# Run from the repository root, with hooks installed:
#   sh tooling/hooks-test/pre-push-runs-when-push-files-is-empty.sh

. tooling/hooks-test/lib.sh

hooks_test_begin 'pre-push runs even when push_files is empty'

HOOKS_TEST_REMOTE='hooks-test-remote'

bare="$HOOKS_TEST_TMP/remote.git"
git init -q --bare "$bare"
git remote add "$HOOKS_TEST_REMOTE" "$bare"

# No new commit: the scratch branch points at the same object as the branch it came from. This is what makes
# `{push_files}` empty and drives the code path under check.
printf '  running git push of an unchanged branch (the gate must still run — this takes ~20 s)...\n'

push_status=0
git push "$HOOKS_TEST_REMOTE" "HEAD:refs/heads/probe" > "$HOOKS_TEST_TMP/push.log" 2>&1 || push_status=$?

# 1. The gate was not skipped. This is the whole point of the check.
if grep -q 'quality-gate.*(skip)' "$HOOKS_TEST_TMP/push.log"; then
    cat "$HOOKS_TEST_TMP/push.log" >&2
    fail 'the pre-push gate was SKIPPED — a green push would prove nothing (see the pre-push notes in lefthook.yml)'
fi
pass 'the gate was not skipped'

# 2. It actually executed Gradle, rather than being absent from the run altogether.
if ! grep -q 'quality-gate' "$HOOKS_TEST_TMP/push.log"; then
    cat "$HOOKS_TEST_TMP/push.log" >&2
    fail 'the pre-push gate did not appear in the hook output at all'
fi
if ! grep -q 'BUILD SUCCESSFUL' "$HOOKS_TEST_TMP/push.log"; then
    cat "$HOOKS_TEST_TMP/push.log" >&2
    fail 'the gate ran but Gradle did not report BUILD SUCCESSFUL'
fi
pass 'the gate executed the Gradle build to completion'

# 3. Positive control: a green tree is allowed through.
if [ "$push_status" -ne 0 ]; then
    cat "$HOOKS_TEST_TMP/push.log" >&2
    fail "git push failed on a green tree (exit $push_status) — the gate is not merely strict, it is broken"
fi
pass 'git push succeeded on a green tree'

refs=$(git --git-dir="$bare" for-each-ref --format='%(refname)')
if [ "$refs" != 'refs/heads/probe' ]; then
    fail "expected refs/heads/probe on the remote, got: ${refs:-<none>}"
fi
pass 'the ref transferred'

printf '\nPASS: the gate runs on an empty push_files list and lets a green tree through.\n'
