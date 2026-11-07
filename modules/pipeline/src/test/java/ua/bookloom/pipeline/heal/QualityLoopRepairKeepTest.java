package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * The one repair never makes text worse: it replaces the text in place only when the never-worse gate keeps it, and a
 * segment gets no second repair ({@code specs/quality-gates/spec.md} "Keep a repair only when it passes the hard gates
 * and the never-worse gate keeps it"). The texts are chosen by the checks they trip against "He opened the old door.":
 * a Latin letter inside a Cyrillic word is {@code script-purity}, an unclosed guillemet is {@code quote-balance}, an
 * upper-case English echo is {@code script} and {@code echo}, a one-word target is {@code length}.
 */
class QualityLoopRepairKeepTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD = "Він відчинив старі двері.";
    private static final String PURITY_AND_QUOTE = "Він відчиниw «старі «двері.";
    private static final String PURITY_ONLY = "Він відчиниw старі двері.";
    private static final String QUOTE_ONLY = "Він «відчинив «старі двері.";
    private static final String ECHO_AND_QUOTE = "HE OPENED THE «OLD «DOOR.";
    private static final String TOO_SHORT = "Він.";
    private static final String SHORT_WITH_PURITY = "Він відчиниw.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    // A repair that adds a blocking finding must not replace the draft that only failed another check.
    @Test
    void nextDecision_repairThatAddsABlocker_isNotKept() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(QualityLoopTestSupport.itemReply(SHORT_WITH_PURITY)));

        final SegmentOutcome decided = decide(TOO_SHORT, model, true);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(TOO_SHORT);
        assertThat(raisedBy(decided)).contains("length").doesNotContain("script-purity");
        assertThat(model.requests()).hasSize(1);
    }

    // A repair that clears one blocker and adds none stays, even though a blocker is left: it is the best text.
    @Test
    void nextDecision_repairThatClearsOneOfTwoBlockers_isKeptAndTheSegmentFlagged() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(QualityLoopTestSupport.itemReply(QUOTE_ONLY)));

        final SegmentOutcome decided = decide(PURITY_AND_QUOTE, model, true);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(QUOTE_ONLY);
        assertThat(raisedBy(decided)).contains("quote-balance").doesNotContain("script-purity");
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
    }

    // A swap (one blocker traded for another) is no progress, so the text in place stays.
    @Test
    void nextDecision_repairThatSwapsOneBlockerForAnother_keepsTheDraft() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(QualityLoopTestSupport.itemReply(QUOTE_ONLY)));

        final SegmentOutcome decided = decide(PURITY_ONLY, model, true);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(PURITY_ONLY);
        assertThat(raisedBy(decided)).contains("script-purity").doesNotContain("quote-balance");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_repairThatPasses_isAcceptedAsRepaired() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(QualityLoopTestSupport.itemReply(GOOD)));

        final SegmentOutcome decided = decide(PURITY_ONLY, model, true);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.machineTarget()).isEqualTo(GOOD);
    }

    @Test
    void nextDecision_repairThatLeavesABlocker_getsNoSecondRepair() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(QualityLoopTestSupport.itemReply(QUOTE_ONLY)))
                .answer(readable(QualityLoopTestSupport.itemReply(GOOD)));

        final SegmentOutcome decided = decide(ECHO_AND_QUOTE, model, true);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(raisedBy(decided)).contains("quote-balance").doesNotContain("echo", "script");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_noFixingCallAllowed_makesNoRepairCall() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(QualityLoopTestSupport.itemReply(GOOD)));

        final SegmentOutcome decided = decide(PURITY_ONLY, model, false);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(model.requests()).isEmpty();
    }

    // The placeholder gate restored the text fine; only a text check blocked it, so its words are the machine target.
    @Test
    void nextDecision_repairThatChangesNothing_keepsTheDraftAsTheMachineTarget() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(QualityLoopTestSupport.itemReply(QUOTE_ONLY)));

        final SegmentOutcome decided = decide(QUOTE_ONLY, model, true);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(QUOTE_ONLY);
        assertThat(decided.maskedMachineTarget()).isEqualTo(QUOTE_ONLY);
        assertThat(model.requests()).hasSize(1);
    }

    // The typography pass turns a lone straight quote into an unclosed guillemet, so the checks must read that text.
    @Test
    void nextDecision_repairWhoseNormalisedTextLeavesAQuoteOpen_isNotAccepted() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"items\":[{\"id\":\"1\",\"target\":\"Він \\\"відчинив \\\"старі двері.\"}]}"));
        final GateFunction gate = TypographyGate.around(QualityLoopFixtures.PASSTHROUGH_GATE, "uk");

        final SegmentOutcome decided = decide(PURITY_AND_QUOTE, model, true, gate);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(raisedBy(decided)).contains("quote-balance");
    }

    private SegmentOutcome decide(final String draft, final ScriptedChatModel model, final boolean repairs) {
        return decide(draft, model, repairs, QualityLoopFixtures.PASSTHROUGH_GATE);
    }

    private SegmentOutcome decide(
            final String draft, final ScriptedChatModel model, final boolean repairs, final GateFunction gate) {
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), draft, draft, draft, null));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(RepairMode.ONE, 0, false, ProofreadCoverage.NONE),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), repairs ? settings : settings.withoutRepair(), gate, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }

    private static List<String> raisedBy(final SegmentOutcome decided) {
        return decided.findings().stream().map(QaFinding::raisedBy).toList();
    }
}
