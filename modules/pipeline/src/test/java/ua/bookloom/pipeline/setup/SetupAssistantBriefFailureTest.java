package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Register;

/** How the brief suggestion meets a failed or stopped call (15i.B4): one bad window degrades, a stop ends it. */
class SetupAssistantBriefFailureTest extends SetupAssistantBriefTestBase {

    private static Result<ChatResponse> down() {
        return Result.err(AppError.of(ErrorCode.unreachable, "Down", "The server is not answering."));
    }

    private static Result<ChatResponse> stopped() {
        return Result.err(AppError.of(ErrorCode.cancelled, "Stopped", "The call was stopped."));
    }

    // IF one failed window failed the suggestion, THEN a flaky connection would throw away three good readings.
    @Test
    void suggestBrief_oneWindowCallFails_isMadeFromTheOthersAndTheFailedOneCountsAgainstEveryMajority() {
        knowledgeAnswers(KNOWN);
        model.answerTo("brief-suggestion", down());
        windowsAnswer(
                window("crime story", "casual"), window("crime story", "casual"), window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        final BriefSuggestion suggestion = suggested();

        assertThat(requestsOf(CallKind.BRIEF_SUGGESTION)).hasSize(WINDOWS);
        assertThat(requestsOf(CallKind.BRIEF_SYNTHESIS)).hasSize(1);
        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER))
                .hasValueSatisfying(proof -> assertThat(proof.describe()).isEqualTo("from 3 of 4 passages"));
    }

    @Test
    void suggestBrief_everyWindowCallFails_returnsTheFirstErrorAndAsksNoSynthesis() {
        knowledgeAnswers(KNOWN);
        windowsFail(WINDOWS);

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error().code()).isEqualTo(ErrorCode.unreachable);
        assertThat(requestsOf(CallKind.BRIEF_SUGGESTION)).hasSize(WINDOWS);
        assertThat(requestsOf(CallKind.BRIEF_SYNTHESIS)).isEmpty();
    }

    private void windowsFail(final int times) {
        IntStream.range(0, times).forEach(index -> model.answerTo("brief-suggestion", down()));
    }

    // IF a stop were read as a failed window, THEN pressing Stop would go on to read the other windows.
    @Test
    void suggestBrief_windowCallStopped_endsAtOnceAsCancelled() {
        knowledgeAnswers(KNOWN);
        model.answerTo("brief-suggestion", stopped());

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error().code()).isEqualTo(ErrorCode.cancelled);
        assertThat(requestsOf(CallKind.BRIEF_SUGGESTION)).hasSize(1);
        assertThat(requestsOf(CallKind.BRIEF_SYNTHESIS)).isEmpty();
    }

    // IF a stop during the synthesis left four readings applied, THEN Stop would still change the brief.
    @Test
    void suggestBrief_synthesisCallStopped_isCancelledNotASuggestion() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        model.answerTo("brief-synthesis", stopped());

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.data()).isNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.cancelled);
    }

    @Test
    void suggestBrief_knowledgeQuestionStopped_isCancelledAndReadsNoWindow() {
        model.answerTo("book-knowledge", stopped());

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error().code()).isEqualTo(ErrorCode.cancelled);
        assertThat(model.requests()).hasSize(1);
    }

    // IF the synthesis were asked when nothing could stand on it, THEN a call would be spent to decide nothing.
    @Test
    void suggestBrief_bookUnknownAndNoWindowReadGenreAudienceOrVoice_asksNoSynthesis() {
        knowledgeAnswers(KNOWN.replace("0.9}", "0.3}"));
        everyWindowAnswers("{" + field("register", "casual", QUOTE) + "}");

        final BriefSuggestion suggestion = suggested();

        assertThat(requestsOf(CallKind.BRIEF_SYNTHESIS)).isEmpty();
        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
    }
}
