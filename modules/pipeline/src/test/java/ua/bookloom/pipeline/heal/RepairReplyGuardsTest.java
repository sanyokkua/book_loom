package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.targetReply;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.KnownRed;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * What a directed fix may hand back (15i.T2): a reply with a stray quote code at its start or end is read, and a fix
 * that mends one finding by bringing a Russian letter is not taken over the draft. The cases still wrong today sit in
 * {@link KnownRed#until}; the paired cases without it hold before and after.
 */
class RepairReplyGuardsTest {

    private static final String T2 = "15i.T2";
    private static final String QUOTE_FREE_SOURCE = "He opened the old door.";
    private static final String PLAIN_SOURCE = "Він відчинив старі двері.";
    private static final DirectedFix FIX =
            new DirectedFix(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()));
    private static final QaFinding ALPHABET_FINDING =
            new QaFinding("language", Severity.LOW, "the word has the letter ы", "alphabet");

    private static final String DRAFT = "Він відчинив старі дверзі.";
    private static final String SWAP_DOOR_EDIT =
            "{\"criterion\":\"meaning\",\"quote\":\"дверзі\",\"replacement\":\"вікна\"}";
    private static final String RUSSIAN_LETTER_FIX = "Він відчинив стары двері.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

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
                "{\"target\":\"\\u001f Він відчинив старі двері.\"}"
            })
    void fix_replyWithAStrayControlCodeAtAnEndOfAQuoteFreeText_returnsTheTextWithoutIt(final String reply) {
        final Result<RepairReply> result = fix(reply);

        KnownRed.until(
                T2,
                "fix-stray-code-" + reply.hashCode(),
                () -> assertThat(result.data()).isEqualTo(new RepairReply.Rewritten(PLAIN_SOURCE)));
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

    // IF a fix that clears the reviewer's quote by writing a Russian letter were taken, THEN the repair would swap one
    // defect for another and the best-text rule would keep the worse one.
    @Test
    void nextDecision_directedFixThatBringsARussianLetter_isDiscardedAndTheDraftStays() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(reviewReply()))
                .answer(readable(targetReply(RUSSIAN_LETTER_FIX)))
                .answer(readable(targetReply(RUSSIAN_LETTER_FIX)));

        final SegmentOutcome decided = decide(model);

        KnownRed.until(T2, "reviewer-fix-russian-letter", () -> {
            assertThat(decided.machineTarget()).isEqualTo(DRAFT);
            assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        });
    }

    private static String reviewReply() {
        return "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[" + SWAP_DOOR_EDIT + "]}]}";
    }

    private SegmentOutcome decide(final ScriptedChatModel model) {
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), DRAFT, DRAFT, DRAFT, null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED,
                DialParameters.of(QualityDial.BALANCED),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }
}
