#!/usr/bin/env sh
#
# Runs every hook check in sequence. Expect minutes, not seconds — the pre-push check runs the full gate.
#
#   sh tooling/hooks-test/run-all.sh

set -eu

sh tooling/hooks-test/pre-commit-formats-staged-file.sh
sh tooling/hooks-test/pre-push-runs-when-push-files-is-empty.sh
sh tooling/hooks-test/pre-push-blocks-checkstyle-violation.sh

printf '\nAll hook checks passed.\n'
