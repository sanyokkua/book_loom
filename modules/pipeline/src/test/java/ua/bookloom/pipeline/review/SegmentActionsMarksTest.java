package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.stored;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** A saved edit never has a formatting mark put back for the person: a missing or doubled mark is refused. */
class SegmentActionsMarksTest {

    private static final String ID = "Book.md:4";
    private static final String OLD_DOOR = "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.";

    @TempDir
    private Path tempDir;

    // IF a lost or repeated mark were repaired silently, THEN the saved words would not be the ones the person typed.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Він відчинив старі⟦g1⟧ двері.",
                "Він відчинив ⟦g0⟧старі двері.",
                "Він відчинив старі двері.",
                "Він відчинив ⟦g0⟧старі⟦g1⟧⟦g1⟧ двері.",
                "Він відчинив ⟦g0⟧⟦g0⟧старі⟦g1⟧ двері."
            })
    void saveEdit_flaggedWithAMissingOrDoubledMark_isValidationAndChangesNothing(final String edit) {
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, ID, OLD_DOOR);
        final SegmentRecord before = stored(desk, ID);

        final Result<SegmentRecord> result = desk.actions().saveEdit(desk.projectId(), ID, edit);

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, ID)).isEqualTo(before);
        assertThat(stored(desk, ID).status()).isEqualTo(SegmentStatus.FLAGGED);
    }

    // The same refusal on an already edited segment leaves its saved edit as it was.
    @Test
    void saveEdit_revisedWithADoubledMark_isValidationAndKeepsTheSavedEdit() {
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, ID, OLD_DOOR);
        ok(desk.actions().saveEdit(desk.projectId(), ID, "Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері."));
        final SegmentRecord before = stored(desk, ID);

        final Result<SegmentRecord> result =
                desk.actions().saveEdit(desk.projectId(), ID, "Він рвучко відчинив ⟦g0⟧старі⟦g1⟧⟦g1⟧ двері.");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(desk, ID)).isEqualTo(before);
        assertThat(stored(desk, ID).status()).isEqualTo(SegmentStatus.REVISED);
    }

    // A complete edit with other words still saves.
    @Test
    void saveEdit_everyMarkOnceWithNewWords_saves() {
        final Desk desk = ReviewFixtures.markdown(tempDir);
        flag(desk, ID, OLD_DOOR);

        final SegmentRecord record =
                ok(desk.actions().saveEdit(desk.projectId(), ID, "Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері."));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
    }

    private static SegmentRecord ok(final Result<SegmentRecord> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }

    private static AppError error(final Result<?> result) {
        return Objects.requireNonNull(result.error(), () -> "expected an error but got " + result.data());
    }
}
