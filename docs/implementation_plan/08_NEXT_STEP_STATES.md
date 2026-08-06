# Next-step states

Relocated verbatim from `AGENTS.md` (agent-memory-standard migration) so the "End every turn with
Next step" section there can stay within its line budget. `AGENTS.md`'s Next-step block format and
its "be honest about state" rules are unchanged and still binding — this file is only the lookup
table of *which command and prompt content* apply to each state the work can be in.

| Where the work just landed | Command | What the prompt must carry |
|---|---|---|
| Nothing in flight | `/opsx:propose` | The backlog entry name and number, its capability + NEW/MOD, the phase file and FR area to read as source material |
| The shape of the change is genuinely unclear | `/opsx:explore` | The question being resolved and what a good answer would let us decide |
| Artifacts generated, unreviewed | *(review — no command)* | The five R1–R5 checks to apply, and that `openspec validate` will **not** catch a lazy `Source:` block or an abstract scenario |
| Artifacts reviewed and good | `openspec validate <change> --strict` | — |
| Validation failed, or review found the plan wrong | `/opsx:update` | Which artifact is wrong, why, and that the four artifacts must stay coherent — do not hand-patch one |
| Validated, not started | `/opsx:apply` | The change name, which task group to start with, and the test types this change implies |
| Tasks partially done | `/opsx:apply` | The exact next unchecked task number, and what the previous group actually produced |
| All tasks checked, gate not run | `./gradlew clean build check spotlessCheck` | That "written" ≠ "verified"; report the real output, never assert green |
| Gate green, not reviewed | Spawn `spec-conformance-reviewer` | The change name and that the reviewer checks the *code* honours the requirements, where `openspec validate` only checked artifact shape |
| Gate red | Spawn `debugger` | The failing task/test, the actual output, and that the fix stays inside the change's scope |
| Reviewed and passing | `openspec archive <change>` | Note `--skip-specs` is the fallback if archive objects to a zero-delta change |
| Archived, stage incomplete | `/opsx:propose` | The next backlog entry; confirm its stage dependencies are satisfied |
| Archived, stage complete | `bash scripts/fr-coverage.sh` | Read the output as advisory; then the first entry of the next stage |
| A spec gap or a genuinely new decision surfaced | *(decision — no command)* | Which frozen clause is wrong or silent, and that the remedy is a **new ADR**, never a spec edit |
