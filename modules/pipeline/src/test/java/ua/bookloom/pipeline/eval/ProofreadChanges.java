package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;

/**
 * Maps each proofread verdict a mini run stored to the {@link ProofreadChange} the {@code PROOFREAD_NEVER_WORSE}
 * grader reads (task 13.9): the failing-check count of the text before the stage and of the text it left, each counted
 * by the eval's own scorer over the expectations that concern that block alone (every-block ones and the block's own;
 * a group expectation reads several blocks and is left out, the glossary is not text).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProofreadChanges {

    /** The id of the run-level result in the report. */
    static final String RESULT_ID = "proofread-never-worse";

    private static final String DEFECT = "proofread made a text worse";

    /**
     * Scores a run and, when its Proofread stage read any segment, adds the {@code PROOFREAD_NEVER_WORSE} result.
     *
     * @param expectations the non-null expectations to meet
     * @param run the non-null run
     * @return the scorer's results, the proofread result last when the stage read a segment; never null
     */
    static List<MiniResult> scored(final MiniExpectations expectations, final MiniRun run) {
        final List<MiniResult> results = new ArrayList<>(MiniScorer.score(expectations, run));
        if (run.proofread().isEmpty()) {
            log.debug("Mini proofread grader skipped: no segment read");
            return List.copyOf(results);
        }
        final MiniScorer.Verdict verdict = MiniGraders.proofreadNeverWorse(of(expectations, run));
        results.add(new MiniResult(
                RESULT_ID,
                DEFECT,
                verdict.ok(),
                verdict.detail(),
                CheckKind.PROOFREAD_NEVER_WORSE.level(),
                List.of(),
                List.of()));
        return List.copyOf(results);
    }

    /**
     * The run's verdicts as the grader reads them.
     *
     * @param expectations the non-null expectations whose failures are counted
     * @param run the non-null run
     * @return one change per segment the stage read, in the run's order; never null
     */
    static List<ProofreadChange> of(final MiniExpectations expectations, final MiniRun run) {
        Objects.requireNonNull(expectations, "expectations");
        Objects.requireNonNull(run, "run");
        return run.proofread().stream()
                .map(read -> {
                    final MiniExpectations own = ownOf(expectations, read.id());
                    final ProofreadChange change = new ProofreadChange(
                            read.id(),
                            read.isKept(),
                            failing(own, run, read.before()),
                            failing(own, run, read.after()));
                    log.debug("Mini proofread change {} outcome={}", change, read.outcome());
                    return change;
                })
                .toList();
    }

    // The every-block expectations and those of this block alone; group and glossary checks read more than one text.
    private static MiniExpectations ownOf(final MiniExpectations expectations, final String id) {
        final List<Expectation> segment = expectations.segments().stream()
                .filter(e -> id.equals(e.segment())
                        || (e.segment() == null && e.group().equals(List.of(id))))
                .filter(e -> !MiniGraders.isRunLevel(e.check()))
                .toList();
        return new MiniExpectations(expectations.every(), segment, List.of());
    }

    private static int failing(final MiniExpectations own, final MiniRun run, final Outcome text) {
        final MiniRun alone = new MiniRun(
                run.policy(), run.end(), Map.of(text.id(), text), List.of(), List.of(), run.calls(), run.elapsed());
        return (int)
                MiniScorer.score(own, alone).stream().filter(r -> !r.passed()).count();
    }
}
