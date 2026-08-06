#!/usr/bin/env sh
#
# commit-msg hook: Conventional Commits validation
# (docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#lefthook-stages).
#
# The history has to stay machine-readable: the release tooling that arrives in change 27 derives the changelog
# and the version bump from the commit type and the breaking-change marker. A message that does not parse is a
# release note that silently goes missing, which is why this is a hard failure at commit time rather than a
# review comment.
#
# Accepted header form (Conventional Commits 1.0.0):
#
#     <type>[optional (scope)][optional !]: <description>
#
# A `!` before the colon, or a `BREAKING CHANGE:` footer, marks a breaking change.
#
# Arguments: $1 is the path to the commit-message file, supplied by lefthook as `{1}`.

set -eu

msg_file="$1"

# Git's own generated messages are exempt: a merge or revert header is fixed by git, and a fixup!/squash!
# subject is rewritten by `git rebase --autosquash` before it ever reaches the published history.
first_line=$(sed -n '1p' "$msg_file")

case "$first_line" in
    "Merge "* | "Revert "* | "fixup!"* | "squash!"* | "amend!"*)
        exit 0
        ;;
esac

# The types are the Conventional Commits set plus `revert`; anything outside it is a typo, not a new category.
types='build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test'

if printf '%s' "$first_line" | grep -Eq "^($types)(\([a-z0-9._/-]+\))?!?: .+"; then
    exit 0
fi

cat >&2 <<EOF
commit-msg: the commit message is not a Conventional Commit.

  got: $first_line

Expected: <type>[(scope)][!]: <description>
  type:  build chore ci docs feat fix perf refactor revert style test
  scope: optional, lowercase, e.g. (llm) (document) (build-logic)
  !:     optional, marks a breaking change

Examples:
  feat(llm): add Ollama-native chat client
  fix(document): keep the EPUB mimetype entry STORED and first
  chore!: drop the retired traceability Gradle tasks

Bypass for a genuinely exceptional commit with: git commit --no-verify
EOF

exit 1
