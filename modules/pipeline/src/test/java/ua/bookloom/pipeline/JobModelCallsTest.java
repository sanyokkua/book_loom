package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.pipeline.run.JobModelCalls;

/** The one place a run's model calls go through: each is announced as it goes out, and a refused one is not. */
class JobModelCallsTest {

    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Hello.")));

    private final JobControl control = new JobControl(Set.of());
    private final ScriptedChatModel model = TranslationJobTestSupport.replies("ONE.", "TWO.");
    private final List<ModelCallStarted> announced = new CopyOnWriteArrayList<>();
    private final JobModelCalls calls =
            new JobModelCalls(onSent -> new CancellableChatModel(model, control, onSent), announced::add);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    // The old announcement named DRAFT for every call, so a screen could not tell a slow repair from a slow draft.
    @Test
    void call_structuralRepair_announcesStructuralRepairForItsSegment() {
        final Result<ChatResponse> result = calls.call(CallKind.STRUCTURAL_REPAIR, "Book.md:0", REQUEST);

        assertThat(result.isOk()).isTrue();
        assertThat(announced).containsExactly(new ModelCallStarted("Book.md:0", CallKind.STRUCTURAL_REPAIR));
        assertThat(model.requests()).hasSize(1);
    }

    // A judge call scores a whole chunk, so it names no segment.
    @Test
    void call_judgeWithoutSegment_announcesJudgeWithNoSegmentId() {
        calls.call(CallKind.JUDGE, null, REQUEST);

        assertThat(announced).containsExactly(new ModelCallStarted(null, CallKind.JUDGE));
    }

    // An announcement for a call that never went out would start a "waiting for the model" clock over nothing.
    @Test
    void call_pauseRequested_announcesNothingAndReachesNoModel() {
        control.pause();

        final Result<ChatResponse> result = calls.call(CallKind.DRAFT, "Book.md:0", REQUEST);

        final AppError error = result.error();
        assertThat(error).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.cancelled);
        assertThat(announced).isEmpty();
        assertThat(model.requests()).isEmpty();
    }
}
