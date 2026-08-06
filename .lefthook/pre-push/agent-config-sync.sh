#!/usr/bin/env sh
#
# pre-push: agent memory config drift check.
#
# Asserts AGENTS.md/CLAUDE.md and the .agents/.claude/.codex skill/agent mirrors are in sync
# (scripts/sync-agent-files.py --check) — the same check CI runs. A push that lets these drift is
# how .claude/skills and .codex/skills silently diverged in the first place (no automated check
# existed before this).
#
# It lives here rather than folded into quality-gate.sh on purpose: quality-gate.sh's whole
# reason to exist is running character-for-character the same command CI's quality job runs
# (design.md D8) — adding an unrelated check there would break that identity. This is its own
# script, as a separate `scripts:` entry, for the same reason quality-gate.sh is a script and not
# a `jobs:` entry: lefthook filters pre-push jobs against `{push_files}` and skips them when that
# list is empty (ordinary pushes), but scripts are not file-filtered and always run.
#
# Bypass with `git push --no-verify` and let CI be the gate instead.

set -eu

exec python3 scripts/sync-agent-files.py --check
