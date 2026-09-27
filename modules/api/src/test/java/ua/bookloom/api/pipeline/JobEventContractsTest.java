package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
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
}
