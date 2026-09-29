package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A backward-revision proposal is built on the person's wording; once they save other wording or revert, the proposal
 * no longer fits and is withdrawn — its deferral stays open, without the proposal, so the next pass builds a new one.
 */
class SegmentActionsProposalTest {

    private static final String SAM_LEFT = "ch02.xhtml:6";
    private static final String RAIN = "ch02.xhtml:3";
    private static final String OLD_EDIT = "Вона пішла.";
    private static final String PROPOSAL = "Він пішов.";

    @TempDir
    private Path tempDir;

    private Desk desk;

    @BeforeEach
    void setUp() {
        desk = ReviewFixtures.epub(tempDir);
        accept(desk, SAM_LEFT, OLD_EDIT);
        ok(desk.actions().saveEdit(desk.projectId(), SAM_LEFT, OLD_EDIT));
        ok(desk.deferrals().add(carrier(PROPOSAL)));
    }

    // ch2 · p07 holds "Він пішов." built on "Вона пішла."; saving "Вона вийшла." withdraws it and keeps the deferral.
    @Test
    void saveEdit_proposalWaiting_withdrawsTheProposalAndKeepsTheDeferralOpen() {
        ok(desk.actions().saveEdit(desk.projectId(), SAM_LEFT, "Вона вийшла."));

        assertThat(view(SAM_LEFT).proposal()).isNull();
        assertThat(view(SAM_LEFT).userTarget()).isEqualTo("Вона вийшла.");
        assertThat(open()).containsExactly(carrier(null));
    }

    // Revert on a REVISED segment withdraws its waiting proposal the same way.
    @Test
    void revert_proposalWaiting_withdrawsTheProposalAndKeepsTheDeferralOpen() {
        ok(desk.actions().revert(desk.projectId(), SAM_LEFT));

        assertThat(view(SAM_LEFT).proposal()).isNull();
        assertThat(view(SAM_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(open()).containsExactly(carrier(null));
    }

    // An edit of another segment leaves ch2 · p07's proposal where it is.
    @Test
    void saveEdit_otherSegment_leavesTheWaitingProposal() {
        accept(desk, RAIN, "Дощ ущух надвечір.");

        ok(desk.actions().saveEdit(desk.projectId(), RAIN, "Дощ ущух лише надвечір."));

        assertThat(view(SAM_LEFT).proposal()).isEqualTo(PROPOSAL);
        assertThat(open()).containsExactly(carrier(PROPOSAL));
    }

    // A deferral that carries no proposal is not touched by an edit of its segment.
    @Test
    void saveEdit_deferralWithoutProposal_leavesItAsItWas() {
        ok(desk.deferrals().resolve(desk.projectId(), "d1"));
        ok(desk.deferrals().add(carrier(null)));

        ok(desk.actions().saveEdit(desk.projectId(), SAM_LEFT, "Вона вийшла."));

        assertThat(open()).containsExactly(carrier(null));
    }

    // With the proposal withdrawn, Apply has nothing to apply and changes nothing.
    @Test
    void acceptProposal_afterTheEditWithdrewIt_isRefused() {
        ok(desk.actions().saveEdit(desk.projectId(), SAM_LEFT, "Вона вийшла."));

        final Result<?> applied = desk.actions().acceptProposal(desk.projectId(), SAM_LEFT);

        assertThat(applied.isErr()).isTrue();
        assertThat(view(SAM_LEFT).userTarget()).isEqualTo("Вона вийшла.");
    }

    private Deferral carrier(@Nullable final String proposal) {
        return new Deferral(
                "d1", desk.projectId(), SAM_LEFT, DeferralReason.GENDER_UNKNOWN, "Sam", null, proposal, proposal);
    }

    private SegmentView view(final String segmentId) {
        return ok(desk.queries().segment(desk.projectId(), segmentId));
    }

    private List<Deferral> open() {
        return ok(desk.deferrals().open(desk.projectId()));
    }

    private static <T> T ok(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }
}
