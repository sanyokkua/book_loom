package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A proofread rewrite that leaves a FLAGGED segment blocked waits as a proposal while the segment stays FLAGGED
 * (design D15 "Reply"); the person applies it from the desk as their own edit, and a later proposal of the same
 * segment replaces the earlier one in the window's commit.
 */
class ProofreadProposalDeskTest {

    private static final String FLAGGED = "ch02.xhtml:6";
    private static final String BLOCKED = "Він пішов.";
    private static final String EARLIER = "Він пішов додому.";
    private static final String LATER = "Він пішов додому пізно.";

    @TempDir
    private Path tempDir;

    private Desk desk;

    @BeforeEach
    void setUp() {
        desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED, BLOCKED);
    }

    @Test
    void acceptProposal_flaggedSegmentWithProofreadProposal_appliedAsTheEdit() {
        ok(desk.deferrals().add(proposal("old", EARLIER)));

        final SegmentRecord record = ok(desk.reviewDesk(ReviewMode.ASSISTED).acceptProposal(desk.projectId(), FLAGGED));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo(EARLIER);
        assertThat(record.path()).isEqualTo(SegmentPath.USER);
        assertThat(ok(desk.deferrals().open(desk.projectId()))).isEmpty();
    }

    // The window that makes a second proposal resolves the first in the same commit; the second is the one applied.
    @Test
    void acceptProposal_secondProposalSupersededTheFirst_appliesTheSecond() {
        ok(desk.deferrals().add(proposal("old", EARLIER)));
        ok(desk.checkpoint()
                .commit(new ChunkCommit(
                        desk.projectId(),
                        List.of(stored(desk, FLAGGED)),
                        List.of(),
                        List.of(),
                        List.of(proposal("new", LATER)),
                        List.of("old"),
                        null)));

        assertThat(ok(desk.deferrals().open(desk.projectId())))
                .extracting(Deferral::id)
                .containsExactly("new");
        final SegmentRecord record = ok(desk.reviewDesk(ReviewMode.ASSISTED).acceptProposal(desk.projectId(), FLAGGED));

        assertThat(record.userTarget()).isEqualTo(LATER);
        assertThat(ok(desk.deferrals().open(desk.projectId()))).isEmpty();
    }

    private Deferral proposal(final String id, final String text) {
        return new Deferral(id, desk.projectId(), FLAGGED, DeferralReason.PROOFREAD, null, null, text, text);
    }

    private static <T> T ok(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }
}
