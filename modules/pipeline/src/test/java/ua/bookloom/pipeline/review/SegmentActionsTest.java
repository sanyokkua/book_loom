package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.MONSTER_TARGET;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.stored;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The five actions over a real opened book, each checked against the segment status machine. */
class SegmentActionsTest {

    private static final String NO_TARGET = "There is no machine translation to accept — edit it or retry.";
    private static final String OLD_DOOR = "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.";
    private static final String BOLD_DOOR = "Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.";

    @TempDir
    private Path tempDir;

    @Test
    void accept_flaggedWithMachineTarget_becomesAcceptedAndReviewed() {
        // FLAGGED -> ACCEPTED keeps the machine target and marks the segment reviewed
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);

        final SegmentRecord record = ok(desk.actions().accept(desk.projectId(), FLAGGED_ID));

        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(record.reviewed()).isTrue();
        assertThat(record.machineTarget()).isEqualTo(MONSTER_TARGET);
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(record);
    }

    @Test
    void accept_acceptedSegment_staysAcceptedWithSameTargetAndIsReviewed() {
        // accepting an ACCEPTED segment is a confirmation: nothing changes but the reviewed mark
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, "ch01.xhtml:2", "Маяк стояв на скелі.");

        final SegmentRecord record = ok(desk.actions().accept(desk.projectId(), "ch01.xhtml:2"));

        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(record.machineTarget()).isEqualTo("Маяк стояв на скелі.");
        assertThat(record.reviewed()).isTrue();
    }

    @Test
    void accept_pendingSegment_isValidationAndStaysPending() {
        // a segment the run has not decided is never accepted by a person
        final Desk desk = ReviewFixtures.epub(tempDir);

        final Result<SegmentRecord> result = desk.actions().accept(desk.projectId(), "ch06.xhtml:0");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, "ch06.xhtml:0").status()).isEqualTo(SegmentStatus.PENDING);
        assertThat(stored(desk, "ch06.xhtml:0").reviewed()).isFalse();
    }

    @Test
    void accept_flaggedWithoutMachineTarget_isValidationWithMessageAndChangesNothing() {
        // Accept would have nothing to keep, so it says what to do instead
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, "ch08.xhtml:5", null);
        final SegmentRecord before = stored(desk, "ch08.xhtml:5");

        final Result<SegmentRecord> result = desk.actions().accept(desk.projectId(), "ch08.xhtml:5");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(error(result).message()).isEqualTo(NO_TARGET);
        assertThat(stored(desk, "ch08.xhtml:5")).isEqualTo(before);
    }

    @Test
    void saveEdit_flaggedWithoutMachineTarget_becomesRevised() {
        // the editor opens on the source, so a person can write the translation over a segment with no target
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, "ch08.xhtml:5", null);

        final SegmentRecord record = ok(desk.actions().saveEdit(desk.projectId(), "ch08.xhtml:5", "Він пішов геть."));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Він пішов геть.");
        assertThat(record.machineTarget()).isNull();
    }

    @Test
    void accept_revisedSegment_isValidationAndKeepsTheEdit() {
        // Accept starts only from FLAGGED or ACCEPTED; an edit is never discarded silently
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, "ch02.xhtml:3", "Дощ ущух лише надвечір.");
        ok(desk.actions().saveEdit(desk.projectId(), "ch02.xhtml:3", "Дощ ущух аж надвечір."));

        final Result<SegmentRecord> result = desk.actions().accept(desk.projectId(), "ch02.xhtml:3");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, "ch02.xhtml:3").status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(stored(desk, "ch02.xhtml:3").userTarget()).isEqualTo("Дощ ущух аж надвечір.");
    }

    @Test
    void accept_unknownSegmentId_isValidation() {
        // an id the project does not hold is refused, not thrown
        final Desk desk = ReviewFixtures.epub(tempDir);

        assertThat(error(desk.actions().accept(desk.projectId(), "ch99.xhtml:0"))
                        .code())
                .isEqualTo(ErrorCode.validation);
    }

    @Test
    void saveEdit_flaggedSegment_becomesRevisedAndKeepsMachineTarget() {
        // Save edit stores the edit and keeps the machine target so it can be reverted to
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);

        final SegmentRecord record =
                ok(desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, "Чудовисько стріло мене опівночі."));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Чудовисько стріло мене опівночі.");
        assertThat(record.maskedUserTarget()).isEqualTo("Чудовисько стріло мене опівночі.");
        assertThat(record.machineTarget()).isEqualTo(MONSTER_TARGET);
        assertThat(record.path()).isEqualTo(SegmentPath.USER);
        assertThat(record.reviewed()).isTrue();
    }

    @Test
    void saveEdit_secondSave_staysRevisedWithTheNewText() {
        // REVISED -> REVISED by another saved edit
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);
        ok(desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, "Чудовисько стріло мене опівночі."));

        final SegmentRecord record =
                ok(desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, "Чудовисько стрілося мені опівночі."));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Чудовисько стрілося мені опівночі.");
    }

    @Test
    void revert_revisedSegment_becomesAcceptedWithBothUserFormsCleared() {
        // Revert restores the machine target: the edit is cleared in both forms
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);
        ok(desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, "Чудовисько стріло мене опівночі."));

        final SegmentRecord record = ok(desk.actions().revert(desk.projectId(), FLAGGED_ID));

        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(record.userTarget()).isNull();
        assertThat(record.maskedUserTarget()).isNull();
        assertThat(record.machineTarget()).isEqualTo(MONSTER_TARGET);
        assertThat(record.reviewed()).isTrue();
        assertThat(record.path()).isEqualTo(SegmentPath.DRAFT);
    }

    @ParameterizedTest
    @EnumSource(
            value = SegmentStatus.class,
            names = {"PENDING", "FLAGGED", "ACCEPTED"})
    void revert_segmentThatIsNotRevised_isValidationAndChangesNothing(final SegmentStatus status) {
        // Revert starts only from REVISED
        final Desk desk = ReviewFixtures.epub(tempDir);
        ReviewFixtures.update(desk, FLAGGED_ID, record -> record.withStatus(status));
        final SegmentRecord before = stored(desk, FLAGGED_ID);

        final Result<SegmentRecord> result = desk.actions().revert(desk.projectId(), FLAGGED_ID);

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(before);
    }

    @Test
    void saveEdit_pendingSegment_isValidationAndChangesNothing() {
        // Save edit starts from FLAGGED, ACCEPTED or REVISED only
        final Desk desk = ReviewFixtures.epub(tempDir);

        final Result<SegmentRecord> result = desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, "Текст.");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, FLAGGED_ID).status()).isEqualTo(SegmentStatus.PENDING);
    }

    @Test
    void saveEdit_markdownEmphasis_keepsMaskedTextAndReadsRestored() {
        // the edit reads with the book's own markup, while the masked text is stored so it can be edited again
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, "Book.md:4", OLD_DOOR);

        final SegmentRecord record = ok(desk.actions().saveEdit(desk.projectId(), "Book.md:4", BOLD_DOOR));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Він рвучко відчинив *старі* двері.");
        assertThat(record.maskedUserTarget()).isEqualTo(BOLD_DOOR);
    }

    @Test
    void saveEdit_secondSaveOfFormattedEdit_staysRevised() {
        // a formatted segment stays editable a second time because its masked text was kept
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, "Book.md:4", OLD_DOOR);
        ok(desk.actions().saveEdit(desk.projectId(), "Book.md:4", BOLD_DOOR));

        final SegmentRecord record = ok(
                desk.actions().saveEdit(desk.projectId(), "Book.md:4", "Він рвучко відчинив ⟦g0⟧старезні⟦g1⟧ двері."));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Він рвучко відчинив *старезні* двері.");
    }

    @Test
    void saveEdit_lockedNameShownAsItsRendering_savedUnchangedReadsRestored() {
        // a locked name is shown as its rendering, so it is not markup the edit has to protect
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, "Book.md:4", "Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері.");

        final SegmentRecord record =
                ok(desk.actions().saveEdit(desk.projectId(), "Book.md:4", "Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері."));

        assertThat(record.userTarget()).isEqualTo("Гейл відчинив *старі* двері.");
    }

    @Test
    void saveEdit_tokensDropped_isValidationNamingThePlaceholderAndKeepsFlagged() {
        // an edit that deletes a placeholder would break the book's markup, so it is refused in place
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, "Book.md:4", OLD_DOOR);
        final SegmentRecord before = stored(desk, "Book.md:4");

        final Result<SegmentRecord> result =
                desk.actions().saveEdit(desk.projectId(), "Book.md:4", "Він відчинив старі двері.");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(error(result).message()).contains("formatting placeholders do not match");
        assertThat(error(result).details()).contains("⟦g0⟧");
        assertThat(stored(desk, "Book.md:4")).isEqualTo(before);
        assertThat(stored(desk, "Book.md:4").status()).isEqualTo(SegmentStatus.FLAGGED);
    }

    @Test
    void skip_flaggedSegment_answersTheNextFlaggedIdAndChangesNothing() {
        // Skip moves the queue on: no change, not reviewed
        final Desk desk = flaggedThree();
        final SegmentRecord before = stored(desk, FLAGGED_ID);

        final Result<String> next = desk.actions().skip(desk.projectId(), FLAGGED_ID);

        assertThat(next.data()).isEqualTo(ReviewFixtures.NAMES_ID);
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(before);
        assertThat(stored(desk, FLAGGED_ID).reviewed()).isFalse();
    }

    @Test
    void skip_lastFlaggedSegment_wrapsToTheFirst() {
        // the queue is a ring: after the last flagged segment comes the first
        final Desk desk = flaggedThree();

        assertThat(desk.actions()
                        .skip(desk.projectId(), ReviewFixtures.SCRIPT_ID)
                        .data())
                .isEqualTo(FLAGGED_ID);
    }

    @Test
    void skip_onlyFlaggedSegment_answersItself() {
        // with no other flagged segment there is nowhere to move on to
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);

        assertThat(desk.actions().skip(desk.projectId(), FLAGGED_ID).data()).isEqualTo(FLAGGED_ID);
    }

    @Test
    void acceptProposal_storedProposal_becomesUserTargetAndResolvesTheDeferral() {
        // a backward-revision proposal changes an edited segment only when the person accepts it
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, "ch02.xhtml:6", "Вона пішла.");
        ok(desk.actions().saveEdit(desk.projectId(), "ch02.xhtml:6", "Вона пішла."));
        desk.deferrals()
                .add(new Deferral(
                        "d1",
                        desk.projectId(),
                        "ch02.xhtml:6",
                        DeferralReason.GENDER_UNKNOWN,
                        "Hale",
                        null,
                        "Він пішов.",
                        "Він пішов."));

        final SegmentRecord record = ok(desk.actions().acceptProposal(desk.projectId(), "ch02.xhtml:6"));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Він пішов.");
        assertThat(record.maskedUserTarget()).isEqualTo("Він пішов.");
        assertThat(record.reviewed()).isTrue();
        assertThat(record.path()).isEqualTo(SegmentPath.USER);
        assertThat(desk.deferrals().open(desk.projectId()).data()).isEmpty();
    }

    @Test
    void acceptProposal_withoutProposal_isValidationAndChangesNothing() {
        // no open deferral holds a proposal for the segment
        final Desk desk = ReviewFixtures.epub(tempDir);
        accept(desk, "ch02.xhtml:6", "Вона пішла.");
        ok(desk.actions().saveEdit(desk.projectId(), "ch02.xhtml:6", "Вона пішла."));
        final SegmentRecord before = stored(desk, "ch02.xhtml:6");

        final Result<SegmentRecord> result = desk.actions().acceptProposal(desk.projectId(), "ch02.xhtml:6");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, "ch02.xhtml:6")).isEqualTo(before);
    }

    private Desk flaggedThree() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);
        flag(desk, ReviewFixtures.NAMES_ID, "Гейл пішов.");
        flag(desk, ReviewFixtures.SCRIPT_ID, "Дім.");
        return desk;
    }

    private static SegmentRecord ok(final Result<SegmentRecord> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }

    private static AppError error(final Result<?> result) {
        return Objects.requireNonNull(result.error(), () -> "expected an error but got " + result.data());
    }
}
