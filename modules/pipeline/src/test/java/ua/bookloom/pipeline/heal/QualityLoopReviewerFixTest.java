package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.targetReply;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;

/**
 * The directed fix for an edit the code could not verify keeps the best text: it is sent only with the reviewer's quote
 * and only when the dial has a repair round, and its result replaces the edited text only when it is no worse and
 * clears at least one quote ({@code specs/quality-gates/spec.md} "Keep the best candidate through the repair path").
 */
class QualityLoopReviewerFixTest {

    private static final String DRAFT = "Він відчинив старі дверзі.";
    private static final String TWO_WORDS_DRAFT = "Він відчинив старі дверзі і стіни.";
    private static final String LATIN_E_EDIT =
            "{\"criterion\":\"invented-word\",\"quote\":\"дверзі\",\"replacement\":\"двeрі\"}";
    private static final String LATIN_I_EDIT =
            "{\"criterion\":\"invented-word\",\"quote\":\"стіни\",\"replacement\":\"стiни\"}";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    private static String reply(final String... edits) {
        return "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[" + String.join(",", edits) + "]}]}";
    }

    @Test
    void nextDecision_refusedEditAndNoRepairRoundInTheDial_sendsNoDirectedFix() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(reply(LATIN_E_EDIT)))
                .answer(readable(targetReply("Він відчинив старі двері.")));

        final SegmentOutcome decided =
                decide(DRAFT, model, DialParameters.of(QualityDial.BALANCED).withoutRepairRounds());

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(DRAFT);
        assertThat(decided.repairRounds()).isZero();
    }

    // The 78-worse pattern on the reviewer's path: a fix that adds a new defect never replaces the edited text.
    @Test
    void nextDecision_directedFixThatAddsABlocker_isDiscardedAndTheDraftStays() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(reply(LATIN_E_EDIT)))
                .answer(readable(targetReply("Він відчинив «старі дверзі.")));

        final SegmentOutcome decided = decide(DRAFT, model, DialParameters.of(QualityDial.BALANCED));

        assertThat(model.requests()).hasSize(2);
        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(DRAFT);
        assertThat(decided.findings()).extracting(QaFinding::raisedBy).doesNotContain("quote-balance");
    }

    // The fix clears one of the two quotes: that text has fewer verified blockers, so it is kept, and the quote it
    // left stays as evidence on the flagged segment.
    @Test
    void nextDecision_directedFixThatClearsOneOfTwoQuotes_isKeptAndTheOtherQuoteRemainsEvidence() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(reply(LATIN_E_EDIT, LATIN_I_EDIT)))
                .answer(readable(targetReply("Він відчинив старі двері і стіни.")));

        final SegmentOutcome decided = decide(TWO_WORDS_DRAFT, model, DialParameters.of(QualityDial.BALANCED));

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo("Він відчинив старі двері і стіни.");
        assertThat(decided.findings())
                .filteredOn(finding -> finding.raisedBy().equals("reviewer"))
                .singleElement()
                .satisfies(finding -> assertThat(finding.note()).contains("стіни"));
    }

    private SegmentOutcome decide(final String draft, final ScriptedChatModel model, final DialParameters dial) {
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), draft, draft, draft, null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED, dial, QualityLoopFixtures.FRAME, NamePolicy.TRANSLITERATE, List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }
}
