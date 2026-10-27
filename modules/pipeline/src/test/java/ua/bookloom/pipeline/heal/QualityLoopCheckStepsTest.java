package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/** The check steps a chunk's reviewer passes announce through the real quality loop. */
class QualityLoopCheckStepsTest {

    private static final String CLEAN_DRAFT = "Він відчинив старі двері.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    private static ModelCalls announcing(final ScriptedChatModel model, final List<JobEvent> events) {
        final StepAnnouncer steps = StepAnnouncer.over(events::add);
        return new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return model.chat(request);
            }

            @Override
            public StepAnnouncer steps() {
                return steps;
            }
        };
    }

    private static DraftOutcome.Drafted cleanDraft() {
        return new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), CLEAN_DRAFT, CLEAN_DRAFT, CLEAN_DRAFT, null);
    }

    private static List<Object> stepTrail(final List<JobEvent> events) {
        return events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .map(change -> (Object) tuple(change.label(), change.state(), change.result()))
                .toList();
    }

    // IF Max's second pass skipped after an unreadable first verdict stayed open, THEN the bar would never finish.
    @Test
    void start_maxUnreadableFirstVerdict_endsSecondCheckNoCall() {
        final List<JobEvent> events = new ArrayList<>();
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable("Looks good to me!"));

        loop.start(
                List.of(cleanDraft()),
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.MAX),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                announcing(model, events));

        assertThat(stepTrail(events))
                .containsExactly(
                        tuple("check-1", StepState.STARTED, null),
                        tuple("check-1", StepState.ENDED, StepResult.DONE),
                        tuple("check-2", StepState.STARTED, null),
                        tuple("check-2", StepState.ENDED, StepResult.NO_CALL));
        assertThat(model.requests()).hasSize(1);
    }

    // A chunk whose only draft was flagged at once has no pair for the reviewer: every planned check ends with no call.
    @Test
    void start_noQualifyingPair_endsChecksNoCall() {
        final List<JobEvent> events = new ArrayList<>();
        final ScriptedChatModel model = new ScriptedChatModel();
        final DraftOutcome flagged = new DraftOutcome.FlaggedAtOnce(
                segment(),
                QualityLoopTestSupport.SOURCE,
                List.of(),
                AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable."));

        loop.start(
                List.of(flagged),
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.MAX),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                announcing(model, events));

        assertThat(stepTrail(events))
                .containsExactly(
                        tuple("check-1", StepState.STARTED, null),
                        tuple("check-1", StepState.ENDED, StepResult.NO_CALL),
                        tuple("check-2", StepState.STARTED, null),
                        tuple("check-2", StepState.ENDED, StepResult.NO_CALL));
        assertThat(model.requests()).isEmpty();
    }

    // The failed reviewer call leaves its check open for the retry; giving up on the reviewer ends it FAILED.
    @Test
    void startWithoutReviewer_afterFailure_endsOpenCheckFailed() {
        final List<JobEvent> events = new ArrayList<>();
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(unreachable));
        final ModelCalls calls = announcing(model, events);
        final var settings = QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.BALANCED);
        loop.start(List.of(cleanDraft()), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls);

        loop.startWithoutReviewer(
                List.of(cleanDraft()), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls, unreachable);

        assertThat(stepTrail(events))
                .containsExactly(
                        tuple("check-1", StepState.STARTED, null),
                        tuple("check-1", StepState.ENDED, StepResult.FAILED));
    }
}
