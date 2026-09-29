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
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.judge.JudgeDeferral;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** A chunk's judge deferrals are the chunk judge's and every re-judge's, so none is lost to a repair. */
class ChunkDeciderTest {

    private static final String SOURCE = "The lighthouse stood on the cliff.";
    private static final String TARGET = "Маяк стояв на прибережній скелі.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void deferrals_afterRejudge_includesTheRejudgesDeferrals() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"score\":0.5,\"verdict\":\"revise\",\"findings\":[],"
                        + "\"deferrals\":[{\"segmentId\":\"s1\",\"reason\":\"who keeps the light\"}]}"))
                .answer(reply("{\"issues\":[]}"))
                .answer(reply("{\"target\":\"" + TARGET + "\"}"))
                .answer(reply("{\"score\":0.9,\"verdict\":\"accept\","
                        + "\"deferrals\":[{\"segmentId\":\"s1\",\"reason\":\"which bay\"}]}"));
        final ChunkDecider decider = Objects.requireNonNull(loop.start(
                        List.of(drafted()),
                        QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.BALANCED),
                        QualityLoopFixtures.PASSTHROUGH_GATE,
                        calls(model))
                .data());

        final SegmentOutcome decided =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(model.requests()).hasSize(4);
        assertThat(decider.deferrals())
                .containsExactly(
                        new JudgeDeferral("Book.md:0", "who keeps the light"),
                        new JudgeDeferral("Book.md:0", "which bay"));
    }

    private static DraftOutcome.Drafted drafted() {
        return new DraftOutcome.Drafted(segment(), SOURCE, List.of(), TARGET, TARGET, TARGET, null);
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

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
