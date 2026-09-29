package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The judge reads the masked form — a hidden name back in place — never the model's reply with its protected-span
 * tokens, which it cannot read; and the segment's own source beside it, never the text the draft was shown.
 */
class QualityLoopJudgeMaskedFormTest {

    private static final String SOURCE =
            "The lighthouse stood on the rocky shore, its beam sweeping across the still dark water tonight.";
    private static final String SHOWN_SOURCE =
            "The ⟦g5⟧ stood on the rocky shore, its beam sweeping across the still dark water tonight.";
    private static final String REPLY_WITH_TOKEN =
            "⟦g5⟧ стояв на прибережній скелі, його промінь ковзав по темній нерухомій воді цієї ночі.";
    private static final String MASKED_FORM =
            "Маяк стояв на прибережній скелі, його промінь ковзав по темній нерухомій воді цієї ночі.";
    private static final GateFunction NAME_RESTORING_GATE =
            (segment, reply) -> new GateResult.Restored(reply.replace("⟦g5⟧", "Маяк"), reply.replace("⟦g5⟧", "Маяк"));

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void start_draftWhoseReplyCarriesASpanToken_showsTheChunkJudgeTheMaskedForm() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{\"score\":0.9,\"verdict\":\"accept\"}"));

        loop.start(List.of(drafted()), settings(), NAME_RESTORING_GATE, calls(model));

        final String judgeUser =
                model.requests().getFirst().messages().getLast().content();
        assertThat(judgeUser)
                .contains("Source: " + SOURCE)
                .contains("Candidate: " + MASKED_FORM)
                .doesNotContain("⟦g5⟧");
    }

    @Test
    void nextDecision_repairedRoundWithASpanToken_showsTheRejudgeTheMaskedForm() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"score\":0.60,\"verdict\":\"revise\",\"findings\":[],\"deferrals\":[]}"))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable("{\"target\":\"" + REPLY_WITH_TOKEN + "\"}"))
                .answer(readable("{\"score\":0.9,\"verdict\":\"accept\"}"));
        final ChunkDecider decider =
                Objects.requireNonNull(loop.start(List.of(drafted()), settings(), NAME_RESTORING_GATE, calls(model))
                        .data());

        decider.nextDecision();

        assertThat(model.requests()).hasSize(4);
        final String rejudgeUser = model.requests().get(3).messages().getLast().content();
        assertThat(rejudgeUser)
                .contains("Source: " + SOURCE)
                .contains("Candidate: " + MASKED_FORM)
                .doesNotContain("⟦g5⟧");
    }

    private static DraftOutcome.Drafted drafted() {
        return new DraftOutcome.Drafted(
                segment(), SHOWN_SOURCE, List.of(), REPLY_WITH_TOKEN, MASKED_FORM, MASKED_FORM, null);
    }

    private static LoopSettings settings() {
        return new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(2, 1, true, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
