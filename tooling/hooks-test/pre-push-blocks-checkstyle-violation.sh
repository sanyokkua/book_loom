#!/usr/bin/env sh
#
# Check: a Checkstyle violation makes `git push` fail BEFORE any ref reaches the remote.
#
# "The hook failed" and "nothing was published" are different claims, and only the second one matters. A
# pre-push hook that runs after the ref transfer, or that git ignores because the exit code was swallowed by a
# pipeline, still fails loudly — and still puts the bad commit on the remote. So this check asserts the remote
# is empty afterwards, not merely that the command exited non-zero.
#
# The remote is a throwaway bare repository in a temp directory. Nothing here can touch `origin`.
#
# This takes minutes: the pre-push hook runs the full `clean build check spotlessCheck` gate, which is the
# whole point of D8 and precisely why these checks are not on the Gradle `check` graph.
#
# Run from the repository root, with hooks installed:
#   sh tooling/hooks-test/pre-push-blocks-checkstyle-violation.sh

. tooling/hooks-test/lib.sh

hooks_test_begin 'pre-push blocks a Checkstyle violation before any ref transfers'

HOOKS_TEST_FIXTURE='modules/util/src/main/java/ua/bookloom/util/paths/HookGateProbe.java'
HOOKS_TEST_REMOTE='hooks-test-remote'

bare="$HOOKS_TEST_TMP/remote.git"
git init -q --bare "$bare"
git remote add "$HOOKS_TEST_REMOTE" "$bare"

# `Bad_Method_Name` violates Checkstyle's `MethodName`. It is chosen because Spotless cannot mask it: Palantir
# Java Format rewrites layout and never touches an identifier, so the file is simultaneously perfectly
# formatted and unambiguously in breach — which isolates this check to the Checkstyle gate. An interface keeps
# it clear of the other gates: no branches for JaCoCo to measure, no constructor for `HideUtilityClassConstructor`,
# and `..paths` is not a carrier package, so the `records-first` ArchUnit rule does not apply.
cat > "$HOOKS_TEST_FIXTURE" <<'EOF'
package ua.bookloom.util.paths;

/** Throwaway fixture written by tooling/hooks-test — never committed to a real branch. */
public interface HookGateProbe {
    void Bad_Method_Name();
}
EOF

# --no-verify on the COMMIT is deliberate: this check is about pre-push, and letting pre-commit run would only
# add a second thing that could explain a failure.
git add -- "$HOOKS_TEST_FIXTURE"
git commit -q --no-verify -m 'test(hooks): probe that pre-push blocks a Checkstyle violation'

printf '  running git push (the pre-push hook runs the full gate — this takes minutes)...\n'

push_status=0
git push "$HOOKS_TEST_REMOTE" "HEAD:refs/heads/probe" > "$HOOKS_TEST_TMP/push.log" 2>&1 || push_status=$?

# 1. The push was rejected.
if [ "$push_status" -eq 0 ]; then
    cat "$HOOKS_TEST_TMP/push.log" >&2
    fail 'git push succeeded despite a Checkstyle violation'
fi
pass "git push exited $push_status"

# 2. Nothing reached the remote. This is the assertion that matters: a bare repo with no refs never received
#    the commit, so the violation was stopped before publication rather than alongside it.
refs=$(git --git-dir="$bare" for-each-ref --format='%(refname)')
if [ -n "$refs" ]; then
    printf '  remote refs found:\n%s\n' "$refs" >&2
    fail 'the remote received refs — the push was not blocked before the transfer'
fi
pass 'the remote holds no refs — nothing was transferred'

# 3. It failed for the seeded reason, not an unrelated red gate.
if ! grep -qi 'checkstyle' "$HOOKS_TEST_TMP/push.log"; then
    cat "$HOOKS_TEST_TMP/push.log" >&2
    fail 'the push failed, but Checkstyle is not named in the output — the gate may have failed for another reason'
fi
pass 'Checkstyle is named as the failing gate'

if ! grep -q 'Bad_Method_Name' "$HOOKS_TEST_TMP/push.log"; then
    printf '  note: the seeded method name is not quoted in the output; Checkstyle findings may be in the HTML report only.\n'
else
    pass 'the seeded method name is quoted in the failure output'
fi

printf '\nPASS: pre-push blocked the violation and no ref reached the remote.\n'
