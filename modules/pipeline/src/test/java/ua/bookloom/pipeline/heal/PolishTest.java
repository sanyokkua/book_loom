package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** {@link Polish}: the optional monolingual smoothing call for a near miss. */
class PolishTest {

    private static final Polish POLISH = new Polish(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()));
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private static final String SOURCE = "She walked into the room.";
    private static final String CANDIDATE = "Вона зайшла в кімнату.";
    private static final String POLISHED_TARGET_REPLY = "{\"target\":\"Вона тихо зайшла в кімнату.\"}";

    @Test
    void polish_anyCall_systemMessageHoldsStyleSheet() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(POLISHED_TARGET_REPLY));

        POLISH.polish(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains(FRAME.styleSheet().text());
    }

    @Test
    void polish_anyCall_textBlockHoldsOnlyCandidateTarget() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(POLISHED_TARGET_REPLY));

        POLISH.polish(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        final String user = model.requests().getFirst().messages().get(1).content();
        assertThat(textBlockCount(user)).isEqualTo(1);
        assertThat(textBlockOf(user)).isEqualTo(CANDIDATE);
        assertThat(user).contains("[Source]").contains(SOURCE);
    }

    @Test
    void polish_anyCall_sendsTemperaturePointTwo() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(POLISHED_TARGET_REPLY));

        POLISH.polish(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        assertThat(model.requests().getFirst().temperature()).isEqualTo(0.2);
    }

    @Test
    void polish_realPseudoModel_returnsUppercaseCandidate() {
        final PseudoChatModel pseudo = new PseudoChatModel(new ObjectMapper());
        final ModelCalls calls = (kind, segmentId, request) -> pseudo.chat(request);

        final Result<RepairReply> result = POLISH.polish(segment(), FRAME, SOURCE, CANDIDATE, calls);

        assertThat(result.data()).isEqualTo(new RepairReply.Rewritten(CANDIDATE.toUpperCase(Locale.ROOT)));
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private static String textBlockOf(final String userMessage) {
        final String opening = "<Text>\n";
        final int start = userMessage.indexOf(opening);
        final int end = userMessage.indexOf("\n</Text>", start + opening.length());
        return userMessage.substring(start + opening.length(), end);
    }

    private static int textBlockCount(final String userMessage) {
        int count = 0;
        int index = 0;
        while ((index = userMessage.indexOf("<Text>\n", index)) != -1) {
            count++;
            index += "<Text>\n".length();
        }
        return count;
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
}
