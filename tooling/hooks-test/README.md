# Hook checks

Scripted checks that the Lefthook stages in `../../lefthook.yml` do what they claim.

```bash
lefthook install                                # once per clone — the checks refuse to run without it
sh tooling/hooks-test/run-all.sh
```

## Why these are scripts and not JUnit tests

They are deliberately **not on the Gradle `check` graph**. A hook check is only meaningful against a real git
repository with real hooks installed and a real `git commit` / `git push` driving them — the thing under test is
git's own invocation of the hook, not a function we could call. Wiring that into `check` would mean either
running `lefthook install` from a build task (a build that silently writes to `.git/hooks` is a bad neighbour)
or reimplementing git's hook dispatch in a test, which would then be checking the reimplementation.

The pre-push check also runs `./gradlew clean build check spotlessCheck` inside a `git push`. Putting that on
the `check` graph would make `check` invoke itself.

So: run them by hand after changing `lefthook.yml` or either script in `tooling/hooks/`.

## What each check proves

| Script | Claim |
|---|---|
| `pre-commit-formats-staged-file.sh` | A mis-formatted staged file is formatted **in the commit object**, not merely on disk — `stage_fixed: true` really re-stages before git builds the commit. |
| `pre-push-runs-when-push-files-is-empty.sh` | The gate **is not skipped** when lefthook computes an empty `{push_files}`, and a green tree is still allowed through. |
| `pre-push-blocks-checkstyle-violation.sh` | A Checkstyle violation makes `git push` fail **before any ref transfers** — asserted by the throwaway bare remote holding no refs afterwards, not just by a non-zero exit code. |

The middle check exists because of a bug the first two rounds of checking missed. Written as a pre-push
`jobs:` entry, the gate is filtered against `{push_files}` and lefthook silently **skips** it when that list is
empty — which it is when pushing an existing branch to a remote that does not have it yet. The gate "passed"
in 0.04 s and the ref transferred anyway. Lefthook 2.x has no job-level opt-out (`skip_empty` is not in the v2
schema, and overriding `files:` does not help — the result is intersected with `{push_files}` regardless), so
the gate is a `scripts:` entry, which is not file-filtered.

The Checkstyle check does **not** cover this: it pushes a branch carrying a new commit, so `{push_files}` is
non-empty and the job runs either way. Two checks that both push new commits would both have stayed green
while the gate was quietly inert on ordinary pushes.

## Safety

The checks mutate this repository, so `lib.sh` constrains them:

- they refuse to run unless the index is empty, so no work of yours is swept into a throwaway commit;
- all work happens on a `hooks-test/$$` scratch branch created from the current `HEAD`;
- an `EXIT` trap restores the original branch and `HEAD` however the script ends;
- the rewind is `git reset --soft` — **never** `--hard`, which would destroy uncommitted work in the tree;
- the push check pushes to a bare repository under `mktemp -d`. `origin` is never contacted.
