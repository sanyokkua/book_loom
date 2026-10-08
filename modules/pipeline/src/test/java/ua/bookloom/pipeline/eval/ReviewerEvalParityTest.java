package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** The reviewer eval resolves a reply with the run's own resolver, so what the run does after a refused edit shows. */
class ReviewerEvalParityTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String CANDIDATE = "Він відчинив старі двері.";

    private static ChatResponse reply(final String content) {
        return new ChatResponse(content, FinishReason.STOP);
    }

    @Test
    void review_editTheVerifierRefuses_isFollowedByTheRunsDirectedFixCall() {
        final String refusedEdit = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":["
                + "{\"criterion\":\"meaning\",\"quote\":\"старі двері\",\"replacement\":\"старі двері}\"}]}]}";
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(reply(refusedEdit)))
                .answer(Result.ok(reply("{\"target\":\"" + CANDIDATE + "\"}")));
        final PromptTemplates templates = new PromptTemplates();
        final ReviewerEval reviews = new ReviewerEval(
                (kind, segmentId, request) -> model.chat(request),
                templates,
                new DirectedFix(templates, new DraftReplyParser(new com.fasterxml.jackson.databind.ObjectMapper())));
        final EvalProject project = EvalProject.of(
                EvalProject.Setup.single("en", "uk", List.of(), EvalContext.none(), SOURCE),
                (kind, segmentId, request) -> model.chat(request));

        final ReviewerEval.Reviewed reviewed = reviews.review(project, CANDIDATE);

        assertThat(model.requests()).hasSize(2);
        assertThat(reviewed.flagged()).isTrue();
    }

    @Test
    void review_rewriteEndingWithAStrayBrace_isCutAndAccepted() {
        final String rewrite = "{\"results\":[{\"id\":\"s1\",\"status\":\"rewrite\","
                + "\"rewrite\":\"Він відчинив старі двері.\\\"}\"}]}";
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.ok(reply(rewrite)));
        final PromptTemplates templates = new PromptTemplates();
        final ReviewerEval reviews = new ReviewerEval(
                (kind, segmentId, request) -> model.chat(request),
                templates,
                new DirectedFix(templates, new DraftReplyParser(new com.fasterxml.jackson.databind.ObjectMapper())));
        final EvalProject project = EvalProject.of(
                EvalProject.Setup.single("en", "uk", List.of(), EvalContext.none(), SOURCE),
                (kind, segmentId, request) -> model.chat(request));

        final ReviewerEval.Reviewed reviewed = reviews.review(project, CANDIDATE);

        assertThat(reviewed.detail()).contains("blockersLeft=0", "accepted=true");
    }
}
