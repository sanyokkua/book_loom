package ua.bookloom.llm.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;

/**
 * A translation counts as cut at a quote mark only when its own source item opens a straight quotation that goes on
 * after where the translation stops; the prompt's own JSON and another item's quote never count.
 */
class QuoteCutTest {

    // What a folded request's user message starts with: the system text and the schema line, full of straight quotes.
    private static final String FOLDED_SYSTEM = "You translate prose.\n\n"
            + "Return exactly one JSON object matching this schema: {\"items\":[{\"id\":\"<id>\",\"target\":\"<t>\"}]}";
    private static final String QUOTED = "Він сказав \"так\" і пішов.";

    @Test
    void isCut_targetStopsAtItsOwnSourceQuote_isCut() {
        final ChatRequest request = text(QUOTED);

        assertThat(QuoteCut.isCut(request, "{\"target\":\"Він сказав \"}")).isTrue();
    }

    @Test
    void isCut_batchItemStopsAtItsOwnSourceQuote_isCut() {
        final ChatRequest request = items("<s id=\"a\">Без лапок.</s>\n<s id=\"b\">" + QUOTED + "</s>");

        assertThat(
                        QuoteCut.isCut(
                                request,
                                "{\"items\":[{\"id\":\"a\",\"target\":\"Без лапок.\"},{\"id\":\"b\",\"target\":\"Він сказав \"}]}"))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"He said:", "Але я—", "Але я –", "Отже,"})
    void isCut_targetEndsOpenButItsSourceHasNoQuote_isNotCut(final String target) {
        final ChatRequest request = text(target);

        assertThat(QuoteCut.isCut(request, "{\"target\":\"" + target + "\"}")).isFalse();
    }

    @Test
    void isCut_anotherItemHoldsTheQuote_isNotCut() {
        final ChatRequest request = items("<s id=\"a\">" + QUOTED + "</s>\n<s id=\"b\">He said:</s>");

        assertThat(QuoteCut.isCut(
                        request,
                        "{\"items\":[{\"id\":\"a\",\"target\":\"Він сказав «так» і пішов.\"},"
                                + "{\"id\":\"b\",\"target\":\"Він сказав:\"}]}"))
                .isFalse();
    }

    @Test
    void isCut_sourceHasAQuoteAndTargetIsWhole_isNotCut() {
        final ChatRequest request = text(QUOTED);

        assertThat(QuoteCut.isCut(request, "{\"target\":\"Він сказав «так» і пішов.\"}"))
                .isFalse();
    }

    @Test
    void isCut_noSchemaAsked_isNotCut() {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "<Text>\n" + QUOTED + "\n</Text>")),
                0.2,
                null,
                ReasoningLevel.OFF);

        assertThat(QuoteCut.isCut(request, "{\"target\":\"Він сказав \"}")).isFalse();
    }

    @Test
    void isCut_replyIdNamesNoSourceItem_isNotCut() {
        final ChatRequest request = items("<s id=\"a\">Без лапок.</s>\n<s id=\"b\">" + QUOTED + "</s>");

        assertThat(QuoteCut.isCut(request, "{\"items\":[{\"id\":\"zz\",\"target\":\"Він сказав \"}]}"))
                .isFalse();
    }

    private static ChatRequest text(final String source) {
        return folded("<Text>\n" + source + "\n</Text>");
    }

    private static ChatRequest items(final String lines) {
        return folded("<Items>\n" + lines + "\n</Items>");
    }

    private static ChatRequest folded(final String body) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, FOLDED_SYSTEM + "\n\n" + body)),
                0.2,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                ReasoningLevel.OFF,
                null,
                null,
                null,
                null,
                CallKind.DRAFT);
    }
}
