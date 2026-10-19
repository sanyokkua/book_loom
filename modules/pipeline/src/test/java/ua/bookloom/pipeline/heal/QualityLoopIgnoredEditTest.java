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
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;

/**
 * A reviewer edit whose own refusal disproves it (15h.E1, the e4b Oct 9 run): a correct draft must not be flagged, and
 * no directed fix must be spent, because the code refused the reviewer's edit and not the draft.
 */
class QualityLoopIgnoredEditTest {

    private static final String DRAFT = "Він відчинив старі двері.";
    private static final String NAMED_DRAFT = "Гейл відчинив старі двері.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    private static String reply(final String criterion, final String quote, final String replacement) {
        return "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"" + criterion
                + "\",\"quote\":\"" + quote + "\",\"replacement\":\"" + replacement + "\"}]}]}";
    }

    // IF a refused glossary-rendering edit flagged the draft, THEN a correct segment waits for the person for nothing.
    @Test
    void nextDecision_editRefusedAsAGlossaryRenderingChange_keepsTheDraftAcceptedWithNoDirectedFix() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(reply("invented-word", "Гейл", "Гейла")))
                .answer(readable(targetReply(NAMED_DRAFT)));

        final SegmentOutcome decided = decide(NAMED_DRAFT, model, List.of("Hale → Гейл"));

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(NAMED_DRAFT);
    }

    // IF a refused case-change edit flagged the draft, THEN a correct segment waits for the person for nothing.
    @Test
    void nextDecision_editRefusedAsACaseChange_keepsTheDraftAcceptedWithNoDirectedFix() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(reply("invented-word", "Він відчинив", "він відчинив")))
                .answer(readable(targetReply(DRAFT)));

        final SegmentOutcome decided = decide(DRAFT, model, List.of());

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(DRAFT);
    }

    private SegmentOutcome decide(final String draft, final ScriptedChatModel model, final List<String> pairs) {
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), draft, draft, draft, null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED,
                DialParameters.of(QualityDial.BALANCED),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of(),
                pairs);
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }
}
