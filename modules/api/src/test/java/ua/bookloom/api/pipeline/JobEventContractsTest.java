package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.SegmentStatus;

/**
 * The extended job-event contracts: kept old constructors default their new components, and the sealed
 * {@link JobEvent} hierarchy stays exhaustively switchable as new record types join it.
 */
class JobEventContractsTest {

    @Test
    void jobProgress_sixArgConstructor_keepsSectionAndDefaultsNewFields() {
        final JobProgress progress = new JobProgress(JobStage.TRANSLATE, 2, 5, 3, 1, 4);

        assertThat(progress.section()).isEqualTo(2);
        assertThat(progress.sections()).isEqualTo(5);
        assertThat(progress.chunk()).isZero();
        assertThat(progress.chunks()).isZero();
        assertThat(progress.autoAccepted()).isZero();
        assertThat(progress.repairedAccepted()).isZero();
    }

    @Test
    void paused_threeArgConstructor_hasNullSegmentId() {
        final Paused paused =
                new Paused(PauseReason.REQUESTED, null, new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 0));

        assertThat(paused.segmentId()).isNull();
    }

    @Test
    void modelCallStarted_oneArgConstructor_hasDraftKind() {
        final ModelCallStarted started = new ModelCallStarted("ch1.xhtml:0");

        assertThat(started.kind()).isEqualTo(CallKind.DRAFT);
    }

    @Test
    void segmentDecided_fourArgConstructor_hasNullDetail() {
        final SegmentDecided decided = new SegmentDecided(
                "ch1.xhtml:0", SegmentStatus.ACCEPTED, null, new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 0));

        assertThat(decided.detail()).isNull();
    }

    @Test
    void segmentStarted_constructor_keepsLocatorAndPosition() {
        final ChunkPosition position = new ChunkPosition(1, 3, 2, 5);
        final SegmentStarted started = new SegmentStarted("ch1.xhtml:0", "ch1 · p01", "He left.", position);

        assertThat(started.locator()).isEqualTo("ch1 · p01");
        assertThat(started.position()).isEqualTo(position);
    }

    @Test
    void switchOverJobEvent_everyPermittedType_compilesWithNoDefault() {
        assertThatCode(() -> describe(new ModelCallStarted("ch1.xhtml:0"))).doesNotThrowAnyException();
    }

    /** Exhaustively switches over every {@link JobEvent} permitted type; a missing case fails compilation. */
    private static String describe(final JobEvent event) {
        return switch (event) {
            case StageStarted started -> "StageStarted";
            case ModelCallStarted started -> "ModelCallStarted";
            case SegmentDecided decided -> "SegmentDecided";
            case Paused paused -> "Paused";
            case Resumed resumed -> "Resumed";
            case Finished finished -> "Finished";
            case SegmentStarted started -> "SegmentStarted";
            case SegmentDrafted drafted -> "SegmentDrafted";
            case ModelCallFinished finished -> "ModelCallFinished";
            case MemoryUpdated updated -> "MemoryUpdated";
            case ContextAssembled assembled -> "ContextAssembled";
            case RoundStarted round -> "RoundStarted";
            case RecoveryWaiting waiting -> "RecoveryWaiting";
        };
    }

    @Test
    void modelCallFinished_holdsElapsedAndUsageEstimatedFlag() {
        final ModelCallFinished finished =
                new ModelCallFinished("ch1.xhtml:0", CallKind.DRAFT, Duration.ofMillis(120), null, 42, true);

        assertThat(finished.elapsed()).isEqualTo(Duration.ofMillis(120));
        assertThat(finished.usageEstimated()).isTrue();
        assertThat(finished.outputChars()).isEqualTo(42);
    }

    @Test
    void modelCallStarted_twoArgConstructor_isAFirstAttemptAboutItsSegment() {
        final ModelCallStarted started = new ModelCallStarted("s-1", CallKind.JUDGE);

        assertThat(started.segmentIds()).containsExactly("s-1");
        assertThat(started.attempt()).isEqualTo(1);
        assertThat(started.maxAttempts()).isEqualTo(1);
        assertThat(started.timeout()).isNull();
        assertThat(started.request()).isNull();
    }

    @Test
    void modelCallStarted_attemptBeyondItsMaximum_isRejected() {
        assertThatThrownBy(() -> new ModelCallStarted(null, CallKind.JUDGE, List.of(), 3, 2, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelCallStarted_segmentIds_areCopied() {
        final List<String> ids = new ArrayList<>(List.of("s-1", "s-2"));
        final ModelCallStarted started = new ModelCallStarted(
                null, CallKind.JUDGE, ids, 2, 2, Duration.ofSeconds(90), new RequestSummary(4_600, 8192, 1024));
        ids.clear();

        assertThat(started.segmentIds()).containsExactly("s-1", "s-2");
        assertThat(started.request()).isEqualTo(new RequestSummary(4_600, 8192, 1024));
    }

    @Test
    void modelCallFinished_failedAttempt_isNotAnswered() {
        final ModelCallFinished failed = new ModelCallFinished(
                "s-1", CallKind.JUDGE, Duration.ofSeconds(90), null, 0, false, List.of("s-1"), 2, ErrorCode.timeout);

        assertThat(failed.isAnswered()).isFalse();
        assertThat(failed.attempt()).isEqualTo(2);
    }

    @Test
    void modelCallFinished_sixArgConstructor_isAnAnsweredFirstAttempt() {
        final ModelCallFinished finished =
                new ModelCallFinished("s-1", CallKind.DRAFT, Duration.ofMillis(120), null, 42, true);

        assertThat(finished.isAnswered()).isTrue();
        assertThat(finished.attempt()).isEqualTo(1);
        assertThat(finished.segmentIds()).containsExactly("s-1");
    }

    @Test
    void roundStarted_roundBeyondTheBudget_isRejected() {
        assertThatThrownBy(() -> new RoundStarted("s-1", 3, 2, 0.8, "meaning"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paused_fourArgConstructor_countsNoPauses() {
        final Paused paused =
                new Paused(PauseReason.ON_ERROR, null, new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 0), "s-1");

        assertThat(paused.pauses()).isZero();
        assertThat(paused.pausesBeforeFlagging()).isZero();
    }
}
