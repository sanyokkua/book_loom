package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniRun.Calls;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;

/** The {@code PROOFREAD_NEVER_WORSE} grader, kept apart from {@link MiniScorerTest} by the file-length limit. */
class MiniScorerProofreadTest {

    private static final String SOURCE = "He walked out.";
    private static final String BEFORE = "Він вийшов.";
    private static final String KEPT = "Він ушёл.";

    @Test
    void proofreadNeverWorse_keptChangeWithMoreFailingChecks_fails() {
        final MiniScorer.Verdict verdict = MiniGraders.proofreadNeverWorse(List.of(
                new ProofreadChange("a", true, 1, 1),
                new ProofreadChange("b", true, 0, 2),
                new ProofreadChange("c", false, 0, 3)));

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("[b]");
    }

    @Test
    void proofreadNeverWorse_noKeptChangeWorse_passes() {
        final MiniScorer.Verdict verdict = MiniGraders.proofreadNeverWorse(
                List.of(new ProofreadChange("a", true, 2, 1), new ProofreadChange("c", false, 0, 3)));

        assertThat(verdict.ok()).isTrue();
    }

    @Test
    void scored_storedKeptVerdictWhoseTextFailsOneCheckMoreThanItsBefore_failingGrade() {
        final MiniRun.Proofread read = MiniRun.Proofread.of("m01", SOURCE, stored(ProofreadOutcome.KEPT, BEFORE));

        final List<MiniResult> results = ProofreadChanges.scored(expectations(), run(read));

        assertThat(ProofreadChanges.of(expectations(), run(read)))
                .containsExactly(new ProofreadChange("m01", true, 0, 1));
        assertThat(results)
                .filteredOn(r -> r.id().equals(ProofreadChanges.RESULT_ID))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.passed()).isFalse();
                    assertThat(r.level()).isEqualTo(CheckKind.Level.HARD);
                    assertThat(r.detail()).contains("[m01]");
                });
    }

    @Test
    void scored_storedRejectedVerdict_beforeIsTheTextItselfAndTheGradePasses() {
        final MiniRun.Proofread read = MiniRun.Proofread.of("m01", SOURCE, stored(ProofreadOutcome.REJECTED, null));

        assertThat(ProofreadChanges.of(expectations(), run(read)))
                .containsExactly(new ProofreadChange("m01", false, 1, 1));
        assertThat(ProofreadChanges.scored(expectations(), run(read)))
                .filteredOn(r -> r.id().equals(ProofreadChanges.RESULT_ID))
                .singleElement()
                .satisfies(r -> assertThat(r.passed()).isTrue());
    }

    @Test
    void scored_runWithNoVerdict_addsNoProofreadResult() {
        assertThat(ProofreadChanges.scored(expectations(), run()))
                .extracting(MiniResult::id)
                .containsExactly("no-russian-letters");
    }

    private static MiniExpectations expectations() {
        return new MiniExpectations(
                List.of(new Expectation(
                        "no-russian-letters",
                        "russian letters",
                        null,
                        List.of(),
                        CheckKind.NO_RUSSIAN_LETTERS,
                        List.of(),
                        List.of())),
                List.of(),
                List.of());
    }

    private static SegmentRecord stored(final ProofreadOutcome outcome, @Nullable final String before) {
        final boolean kept = outcome == ProofreadOutcome.KEPT;
        return new SegmentRecord(
                        "p",
                        "ch01.xhtml:1",
                        "u",
                        0,
                        SegmentKind.PARAGRAPH,
                        kept ? SegmentStatus.REVISED : SegmentStatus.ACCEPTED,
                        KEPT,
                        KEPT,
                        null,
                        null,
                        1.0,
                        null,
                        List.of(),
                        SegmentPath.DRAFT,
                        false,
                        null)
                .withProofread(new ProofreadVerdict(
                        "r1:fp", outcome, "", before, kept ? SegmentStatus.ACCEPTED : null, before, List.of()));
    }

    private static MiniRun run(final MiniRun.Proofread... reads) {
        return new MiniRun(
                NamePolicy.TRANSLITERATE,
                JobState.COMPLETED,
                Map.of("m01", new Outcome("m01", SOURCE, KEPT, SegmentStatus.REVISED, List.of())),
                List.of(),
                List.of(),
                new Calls(1, 1, 1),
                Duration.ofSeconds(1),
                List.of(),
                SummaryGateTally.NONE,
                List.of(reads));
    }
}
