package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptVariants;
import ua.bookloom.pipeline.prompt.PromptVariants.ReviewerVariant;
import ua.bookloom.pipeline.run.RunSettings;

/** Arm 7a's switch: a Balanced run with the reviewer variant OFF sends no reviewer call, ON sends it as built. */
class ReviewerSwitchTest {

    private static final String DRAFT = "Він відчинив старі двері.";
    private static final String CLEAN_REVIEW = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void start_balancedWithReviewerOff_sendsNoReviewerCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(CLEAN_REVIEW));

        final var decided = decide(model, ReviewerVariant.OFF);

        assertThat(model.requests()).isEmpty();
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void start_balancedWithReviewerOn_sendsTheReviewerCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(CLEAN_REVIEW));

        final var decided = decide(model, ReviewerVariant.ON);

        assertThat(model.requests()).hasSize(1);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void forRun_reviewerOff_keepsTheDialsOtherMechanics() {
        final RunSettings off = settings(QualityDial.MAX, ReviewerVariant.OFF);
        final RunSettings on = settings(QualityDial.MAX, ReviewerVariant.ON);

        assertThat(off.dial().reviewPasses()).isZero();
        assertThat(on.dial().reviewPasses()).isEqualTo(2);
        assertThat(off.dial().withReviewerPasses(2)).isEqualTo(on.dial());
    }

    private static RunSettings settings(final QualityDial dial, final ReviewerVariant reviewer) {
        return RunSettings.forRun(
                new RunRequest("p", ReviewMode.UNATTENDED),
                BookBrief.defaults("en").withDial(dial),
                QualityLoopFixtures.FRAME,
                8192,
                PromptVariants.DEFAULTS.withReviewer(reviewer));
    }

    private ua.bookloom.pipeline.heal.SegmentOutcome decide(
            final ScriptedChatModel model, final ReviewerVariant reviewer) {
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), DRAFT, DRAFT, DRAFT, null);
        final RunSettings run = settings(QualityDial.BALANCED, reviewer);
        final LoopSettings settings = new LoopSettings(run.mode(), run.dial(), run.frame(), run.names(), List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }
}
