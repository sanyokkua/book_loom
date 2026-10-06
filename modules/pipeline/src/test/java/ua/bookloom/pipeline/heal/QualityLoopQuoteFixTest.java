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
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;

/**
 * A draft the quote check alone blocks is repaired without a model and accepted; one that stays blocked by a quote or
 * script-purity check keeps its draft as the machine target while flagged, and a draft that is not a translation
 * (an English leftover) keeps none, so export never writes English for a usable draft nor a bad draft for the source.
 */
class QualityLoopQuoteFixTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String UNCLOSED = "Він відчинив «старі двері.";
    private static final String TWO_UNCLOSED = "Він «відчинив «старі двері.";
    private static final String PURITY_ONLY = "Він відчиниw старі двері.";
    private static final String LONG_ENGLISH =
            "The night was cold and the streets were empty when the old man finally opened the door.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void nextDecision_draftWithOnlyAnUnclosedQuote_isRepairedAndAcceptedWithNoModelCall() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final SegmentOutcome decided = decide(SOURCE, UNCLOSED, model, 1);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo("Він відчинив «старі двері».");
        assertThat(decided.findings()).extracting(QaFinding::raisedBy).containsExactly("normalised");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void nextDecision_roundReplyWithOnlyAnUnclosedQuote_isRepairedAndAccepted() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(UNCLOSED)));

        final SegmentOutcome decided = decide(SOURCE, PURITY_ONLY + " «", model, 1);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.machineTarget()).isEqualTo("Він відчинив «старі двері».");
    }

    @Test
    void nextDecision_quoteTheRepairCannotClear_isFlaggedWithItsDraftAsMachineTarget() {
        final SegmentOutcome decided = decide(SOURCE, TWO_UNCLOSED, new ScriptedChatModel(), 0);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(TWO_UNCLOSED);
        assertThat(decided.maskedMachineTarget()).isEqualTo(TWO_UNCLOSED);
        assertThat(decided.findings()).extracting(QaFinding::raisedBy).contains("quote-balance");
    }

    @Test
    void nextDecision_scriptPurityFailureOnly_isFlaggedWithItsDraftAsMachineTarget() {
        final SegmentOutcome decided = decide(SOURCE, PURITY_ONLY, new ScriptedChatModel(), 0);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(PURITY_ONLY);
    }

    @Test
    void nextDecision_englishLeftover_isFlaggedWithNoMachineTarget() {
        final SegmentOutcome decided = decide(LONG_ENGLISH, LONG_ENGLISH, new ScriptedChatModel(), 0);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.findings()).extracting(QaFinding::raisedBy).contains("language-identity");
        assertThat(decided.machineTarget()).isNull();
        assertThat(decided.rejectedTarget()).isEqualTo(LONG_ENGLISH);
    }

    private SegmentOutcome decide(
            final String source, final String draft, final ScriptedChatModel model, final int repairRounds) {
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), source, List.of(), draft, draft, draft, null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(0, repairRounds, 0, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }
}
