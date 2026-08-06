#!/usr/bin/env sh
#
# pre-push: the full mechanical gate.
#
# This is EXACTLY the command the CI quality job runs (.github/workflows/ci.yml) — identical, never a faster
# hook-only subset. That identity is the point: a green push implies a green CI quality job for the same tree
# (design.md D8). CI may add gates it alone can run (license report, OWASP SCA — both need network); it never
# runs a weaker variant of this one.
#
# It lives here rather than inline in lefthook.yml because lefthook filters pre-push *jobs* against
# `{push_files}` and skips them when that list is empty, which happens on ordinary pushes. Scripts are not
# file-filtered. See the pre-push comment block in lefthook.yml for the full reasoning.
#
# Bypass with `git push --no-verify` and let CI be the gate instead.

set -eu

exec ./gradlew clean build check spotlessCheck
