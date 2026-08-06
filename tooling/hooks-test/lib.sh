#!/usr/bin/env sh
#
# Shared harness for the hook checks. Sourced, not executed.
#
# These checks mutate the real repository — they create a commit, and one of them creates a remote — so every
# script that sources this file must call `hooks_test_begin` before touching anything and rely on the EXIT trap
# to put the repository back. The restore path deliberately never uses `git reset --hard`: the working tree
# routinely holds uncommitted work that is none of this test's business, and a hard reset would destroy it.

set -eu

HOOKS_TEST_TMP=""
HOOKS_TEST_BRANCH=""
HOOKS_TEST_ORIGINAL_BRANCH=""
HOOKS_TEST_ORIGINAL_HEAD=""
HOOKS_TEST_FIXTURE=""
HOOKS_TEST_REMOTE=""

fail() {
    printf '\n  FAIL: %s\n' "$1" >&2
    exit 1
}

pass() {
    printf '  ok: %s\n' "$1"
}

# Preconditions, a scratch branch, and a trap that restores the repository however the script exits.
hooks_test_begin() {
    name="$1"

    printf '\n=== %s ===\n' "$name"

    [ -f lefthook.yml ] || fail 'run this from the repository root (no lefthook.yml here)'

    command -v lefthook >/dev/null 2>&1 || fail 'lefthook is not on the PATH — see README.md#git-hooks'
    command -v gitleaks >/dev/null 2>&1 || fail 'gitleaks is not on the PATH — see README.md#git-hooks'

    lefthook check-install >/dev/null 2>&1 || fail 'hooks are not installed — run: lefthook install'

    # The scripts commit whatever is in the index. If the developer has staged work of their own, that work
    # would be swept into the throwaway commit and then unstaged by the restore path — so refuse instead.
    git diff --cached --quiet || fail 'the index is not empty — commit or unstage your work before running this'

    HOOKS_TEST_ORIGINAL_BRANCH=$(git branch --show-current)
    HOOKS_TEST_ORIGINAL_HEAD=$(git rev-parse HEAD)
    HOOKS_TEST_BRANCH="hooks-test/$$"
    HOOKS_TEST_TMP=$(mktemp -d)

    trap hooks_test_restore EXIT

    git switch -q -c "$HOOKS_TEST_BRANCH"
}

# Undo everything, in the reverse order it was created. Every step tolerates having never happened, because
# the trap fires on an early `fail` too.
hooks_test_restore() {
    status=$?

    if [ -n "$HOOKS_TEST_REMOTE" ]; then
        git remote remove "$HOOKS_TEST_REMOTE" 2>/dev/null || true
    fi

    # Rewind any throwaway commit while keeping the working tree exactly as it is: --soft moves the branch
    # pointer only. The fixture then goes back to being untracked, and is deleted.
    if [ -n "$HOOKS_TEST_ORIGINAL_HEAD" ]; then
        git reset -q --soft "$HOOKS_TEST_ORIGINAL_HEAD" 2>/dev/null || true
    fi

    if [ -n "$HOOKS_TEST_FIXTURE" ]; then
        git restore --staged -- "$HOOKS_TEST_FIXTURE" 2>/dev/null || true
        rm -f "$HOOKS_TEST_FIXTURE"
    fi

    if [ -n "$HOOKS_TEST_ORIGINAL_BRANCH" ]; then
        git switch -q "$HOOKS_TEST_ORIGINAL_BRANCH" 2>/dev/null || true
    fi

    if [ -n "$HOOKS_TEST_BRANCH" ]; then
        git branch -q -D "$HOOKS_TEST_BRANCH" 2>/dev/null || true
    fi

    [ -n "$HOOKS_TEST_TMP" ] && rm -rf "$HOOKS_TEST_TMP"

    trap - EXIT
    exit "$status"
}
