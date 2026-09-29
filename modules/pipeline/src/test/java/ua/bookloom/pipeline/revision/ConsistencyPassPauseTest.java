package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_LEFT;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.SegmentActions;

/**
 * A revision call that waited while the person acted on its segment — a pause, then Revert or an edit, then resume —
 * never stores the answer written for the old text: the pass asks once more from the segment as it reads now, and
 * leaves the deferral open if the segment changes again.
 */
class ConsistencyPassPauseTest {

    private static final String REVISION = "revision";
    private static final long WAIT_SECONDS = 30;

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
    }

    // Blocked on "Вона пішла.", the person reverts to "Сем пішов.": the second request is built from the reverted text
    // and its answer, not the first, becomes the machine target.
    @Test
    void run_revertedWhileTheCallWaited_redoesTheCallFromTheRevertedText() throws Exception {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_LEFT, "Сем пішов.", "Сем пішов.");
        ok(actions().saveEdit(projectId(), SAM_LEFT, "Вона пішла."));
        book.recordUnknownGender(SAM_LEFT);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.MALE, false));
        book.model()
                .blockNthRequest(1)
                .answerTo(REVISION, reply("Він пішов."))
                .answerTo(REVISION, reply("Сем тоді пішов."));

        runWhileTheFirstCallWaits(() -> ok(actions().revert(projectId(), SAM_LEFT)));

        assertThat(book.model().requests()).hasSize(2);
        assertThat(book.userMessage(0)).contains("<Text>\nВона пішла.\n</Text>");
        assertThat(book.userMessage(1)).contains("<Text>\nСем пішов.\n</Text>");
        assertThat(book.stored(SAM_LEFT))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::userTarget, SegmentRecord::status)
                .containsExactly("Сем тоді пішов.", null, SegmentStatus.REVISED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    // Blocked on the machine target, the person saves an edit: the redo is built from the edit and waits as a proposal,
    // the edit itself untouched.
    @Test
    void run_editedWhileTheCallWaited_redoesTheCallFromTheEditAsAProposal() throws Exception {
        samLeftWaitingOnFemale();
        book.model()
                .blockNthRequest(1)
                .answerTo(REVISION, reply("Сем пішла."))
                .answerTo(REVISION, reply("Сем пішла геть."));

        runWhileTheFirstCallWaits(() -> ok(actions().saveEdit(projectId(), SAM_LEFT, "Сем пішов геть.")));

        assertThat(book.model().requests()).hasSize(2);
        assertThat(book.userMessage(1)).contains("<Text>\nСем пішов геть.\n</Text>");
        assertThat(book.stored(SAM_LEFT))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::userTarget, SegmentRecord::status)
                .containsExactly("Сем пішов.", "Сем пішов геть.", SegmentStatus.REVISED);
        assertThat(book.openDeferrals())
                .extracting(Deferral::reason, Deferral::proposal)
                .containsExactly(tuple(DeferralReason.GENDER_UNKNOWN, "Сем пішла геть."));
    }

    // The segment changes during both calls: the pass redoes once only, stores neither answer and leaves the deferral
    // open for a later pass.
    @Test
    void run_segmentChangedDuringBothCalls_leavesTheDeferralOpenAfterOneRedo() {
        samLeftWaitingOnFemale();
        book.model().answerTo(REVISION, reply("Сем пішла.")).answerTo(REVISION, reply("Сем пішла геть."));
        final Iterator<String> edits =
                List.of("Сем пішов геть.", "Сем пішов додому.").iterator();
        final ModelCalls editing = (kind, segmentId, request) -> {
            ok(actions().saveEdit(projectId(), SAM_LEFT, edits.next()));
            return book.model().chat(request);
        };

        final ConsistencyReport report = ok(book.runWith(editing));

        assertThat(book.model().requests()).hasSize(2);
        assertThat(report.genderReRenders()).isZero();
        assertThat(book.stored(SAM_LEFT))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::userTarget)
                .containsExactly("Сем пішов.", "Сем пішов додому.");
        assertThat(book.openDeferrals())
                .extracting(Deferral::reason, Deferral::proposal)
                .containsExactly(tuple(DeferralReason.GENDER_UNKNOWN, null));
    }

    private void samLeftWaitingOnFemale() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_LEFT, "Сем пішов.", "Сем пішов.");
        book.recordUnknownGender(SAM_LEFT);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));
    }

    /** Runs the pass on its own thread, does {@code action} while the first revision call is held, then lets it go. */
    private void runWhileTheFirstCallWaits(final Runnable action) throws Exception {
        try (ExecutorService thread = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<ConsistencyReport> running = thread.submit(() -> ok(book.run(true)));
            book.model().awaitRequests(1);
            action.run();
            book.model().release();
            running.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private SegmentActions actions() {
        return book.desk().actions();
    }

    private String projectId() {
        return book.desk().projectId();
    }
}
