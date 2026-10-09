package ua.bookloom.pipeline.heal;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** Small builders the quality-loop tests share: a plain segment, a scripted calls seam and canned replies. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QualityLoopTestSupport {

    static final String SOURCE = "He opened the old door.";

    /** A plain-text segment of {@link #SOURCE} with no placeholder, so a target is its own masked form. */
    static Segment segment() {
        return segment(SegmentKind.PARAGRAPH);
    }

    /** The same segment as {@link #segment()}, parsed from a block of another kind. */
    static Segment segment(final SegmentKind kind) {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                kind,
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

    static String targetReply(final String target) {
        return "{\"target\":\"" + target + "\"}";
    }

    static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
