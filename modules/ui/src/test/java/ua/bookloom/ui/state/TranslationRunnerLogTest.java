package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ProofreadRoundStarted;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.ui.control.TaggedLog;
import ua.bookloom.ui.i18n.Messages;

/** How engine events become activity-log entries and figures. */
class TranslationRunnerLogTest extends RunnerTestBase {

    // ---- the activity log -----------------------------------------------------------------------------------------

    static Stream<Arguments> eventsAndTheirEntries() {
        final JobProgress at = progress(1, 0, 1);
        return Stream.of(
                Arguments.of(
                        Named.<JobEvent>of("accepted", decided("s-7", SegmentStatus.ACCEPTED, at)),
                        log(LogKind.ACCEPTED, "s-7")),
                Arguments.of(
                        Named.<JobEvent>of("flagged", decided("s-8", SegmentStatus.FLAGGED, at)),
                        log(LogKind.SEGMENT_ERROR, "s-8")),
                Arguments.of(
                        Named.<JobEvent>of("paused", new Paused(PauseReason.AFTER_SECTION, null, at)),
                        log(LogKind.MILESTONE, "paused")),
                Arguments.of(Named.<JobEvent>of("resumed", new Resumed(at)), log(LogKind.MILESTONE, "resumed")));
    }

    // IF an event mapped to the wrong kind or arguments, THEN the log would show the wrong colour or the wrong words.
    @ParameterizedTest
    @MethodSource("eventsAndTheirEntries")
    void event_eachKind_becomesTheCatalogueEntryOnTheNextTick(final JobEvent event, final LogEntry expected)
            throws Exception {
        startJob();

        job.emit(event);
        deliverAndTick();

        assertThat(logEntries()).containsExactly(expected);
        job.finish(Result.err(error()));
        awaitState(RunState.FAILED);
    }

    static Stream<Arguments> stagesAndTheirLines() {
        final JobProgress at = progress(1, 0, 1);
        return Stream.of(
                Arguments.of(Named.<JobEvent>of("prepare", new StageStarted(JobStage.PREP, at)), "Prepare"),
                Arguments.of(Named.<JobEvent>of("translate", new StageStarted(JobStage.TRANSLATE, at)), "Translate"),
                Arguments.of(Named.<JobEvent>of("proofread", new StageStarted(JobStage.PROOFREAD, at)), "Proofread"),
                Arguments.of(
                        Named.<JobEvent>of("round 2", new ProofreadRoundStarted(2, 2, 3, 3)),
                        "Proofread · round 2 of 2: whole book"));
    }

    // IF the log said only that "a new stage" started, THEN the person could not tell from it which stage the run is
    // in.
    @ParameterizedTest
    @MethodSource("stagesAndTheirLines")
    void stageEvent_eachStage_logsALineNamingIt(final JobEvent event, final String stage) throws Exception {
        startJob();

        job.emit(event);
        deliverAndTick();

        assertThat(logEntries())
                .singleElement()
                .satisfies(entry -> assertThat(TaggedLog.lineOf(entry, new Messages(() -> Locale.ENGLISH)))
                        .endsWith("Stage started: " + stage + "."));
        job.finish(Result.err(error()));
        awaitState(RunState.FAILED);
    }

    // IF a flagged segment counted as accepted, THEN the two figures the person is shown would swap.
    @Test
    void flaggedSegment_isCountedAsFlaggedNotAccepted() throws Exception {
        startJob();

        job.emit(decided("s-8", SegmentStatus.FLAGGED, progress(0, 1, 4)));
        deliverAndTick();

        assertThat(onFx(() -> mirror.figures().get().flagged())).isEqualTo(1);
        assertThat(onFx(() -> mirror.figures().get().accepted())).isZero();
        assertThat(onFx(() -> mirror.figures().get().total())).isEqualTo(5);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF the mirror were not updated before the terminal state, THEN a screen reacting to COMPLETED would read counts
    // from before the last tick.
    @Test
    void terminalState_afterEventsWithNoTick_arrivesWithTheFinalFiguresAlreadyPublished() throws Exception {
        final List<String> stateWithAccepted = new CopyOnWriteArrayList<>();
        onFx(() -> {
            mirror.runState()
                    .addListener((o, before, after) -> stateWithAccepted.add(
                            after + ":" + mirror.figures().get().accepted()));
            return null;
        });
        startJob();
        emitAccepted(job, 1, 7);

        job.finish(Result.ok(completedReport(7)));
        awaitState(RunState.COMPLETED);

        assertThat(stateWithAccepted).containsExactly("RUNNING:0", "COMPLETED:7");
    }
}
