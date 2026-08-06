#!/usr/bin/env sh
#
# Pre-commit file-size guard (docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#lefthook-stages).
#
# Rejects a commit that stages a file larger than the threshold. The failure mode this exists to catch is
# mechanical, not malicious: a build output, a heap dump, a downloaded model, or a packaged artifact dragged in
# by an over-broad `git add`. Git never forgets a blob, so a 200 MB file committed once is in the clone forever.
#
# Threshold: 4096 KB by default. The largest legitimately tracked file in this repository is the 1.5 MB icon
# source PNG (`docs/specification/assets/icon/BookLoom-source.png`), so 4 MB leaves comfortable headroom for
# derived art while still catching anything that has no business in source control. Override for a one-off with
# `BOOKLOOM_MAX_FILE_SIZE_KB=8192 git commit ...`; skip the whole hook with `git commit --no-verify`.
#
# Arguments: the staged file paths, supplied by lefthook as `{staged_files}`.

set -eu

max_kb="${BOOKLOOM_MAX_FILE_SIZE_KB:-4096}"
max_bytes=$((max_kb * 1024))
violations=0

for path in "$@"; do
    # A staged deletion or rename leaves a path with no blob in the index — nothing to measure.
    size=$(git cat-file -s ":$path" 2>/dev/null) || continue

    if [ "$size" -gt "$max_bytes" ]; then
        printf '  %s KB  %s\n' "$((size / 1024))" "$path" >&2
        violations=$((violations + 1))
    fi
done

if [ "$violations" -gt 0 ]; then
    printf '\nfile-size-guard: %d staged file(s) exceed %s KB.\n' "$violations" "$max_kb" >&2
    printf 'Unstage them, or set BOOKLOOM_MAX_FILE_SIZE_KB if the file genuinely belongs in the repository.\n' >&2
    exit 1
fi
