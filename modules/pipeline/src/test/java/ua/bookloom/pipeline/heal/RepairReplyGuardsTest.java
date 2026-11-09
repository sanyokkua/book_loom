package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * What a directed fix may hand back (15i.T2): a reply with a stray quote code at its start or end is read, and a fix
 * that mends one finding by bringing a Russian letter is not taken over the draft.
 */
class RepairReplyGuardsTest {

    private static final String QUOTE_FREE_SOURCE = "He opened the old door.";
    private static final String PLAIN_SOURCE = "Він відчинив старі двері.";
    private static final DirectedFix FIX =
            new DirectedFix(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()));
    private static final QaFinding ALPHABET_FINDING =
            new QaFinding("language", Severity.LOW, "the word has the letter ы", "alphabet");

    private static Result<RepairReply> fix(final String reply) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(reply));
        return FIX.fix(
                segment(),
                QualityLoopFixtures.FRAME,
                QUOTE_FREE_SOURCE,
                "Він відчинив стары двері.",
                List.of(ALPHABET_FINDING),
                calls(model));
    }

    // IF a quote code at the start of a fix reply made the whole reply malformed, THEN a clean text the model wrote
    // would be thrown away and the draft would keep its finding (the U+001E glitch of the Oct 10 runs).
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "{\"target\":\"\\u001eВін відчинив старі двері.\"}",
                "{\"target\":\"Він відчинив старі двері.\\u001d\"}",
                "{\"target\":\"\\u001f Він відчинив старі двері.\"}",
                "{\"target\":\"\\u0001Він відчинив старі двері.\"}",
                "{\"target\":\"Він відчинив старі двері.\\u0007\"}",
                "{\"target\":\"\\u001eВін відчинив старі двері.\\u001d\"}",
                "{\"target\":\"\\u0001Він відчинив старі двері.\\u0007\"}"
            })
    void fix_controlCodeAtAnEndOrBothEndsOfAQuoteFreeText_returnsTheTextWithoutIt(final String reply) {
        final Result<RepairReply> result = fix(reply);

        assertThat(result.data()).isEqualTo(new RepairReply.Rewritten(PLAIN_SOURCE));
    }

    // A code inside the text is not at an edge: it may only be mapped to a mark, and where it cannot be the reply stays
    // malformed, so stripping the edges never hides a damaged middle.
    @Test
    void fix_replyWithAControlCodeInsideTheText_isStillMalformed() {
        final Result<RepairReply> result = fix("{\"target\":\"Він відчинив\\u0001 старі двері.\"}");

        assertThat(result.data()).isInstanceOf(RepairReply.Malformed.class);
    }

    // The reply the codes were meant to be, with a pair around a quote the source has, still maps to « ».
    @Test
    void fix_pairedCodesAroundAQuoteTheSourceHas_stillMapToGuillemets() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{\"target\":\"\\u001eНе сьогодні\\u001d, — сказала вона.\"}"));

        final Result<RepairReply> result = FIX.fix(
                segment(),
                QualityLoopFixtures.FRAME,
                "\"Not tonight,\" she said.",
                "Не сьогодні, — сказала вона.",
                List.of(ALPHABET_FINDING),
                calls(model));

        assertThat(result.data()).isEqualTo(new RepairReply.Rewritten("«Не сьогодні», — сказала вона."));
    }
}
