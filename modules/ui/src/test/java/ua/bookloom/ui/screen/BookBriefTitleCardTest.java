package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.TooltipProbe;

/**
 * The Title and author card: the rows fill from the stored title and author segments once they are translated, are
 * editable only then, and an edit goes to the review desk as a stored edit of that segment.
 */
class BookBriefTitleCardTest extends BookBriefScreenTestBase {

    private ScriptedReviewDesk desk() {
        return (ScriptedReviewDesk) injector.getInstance(ReviewDesk.class);
    }

    private static SegmentView metadata(
            final String id, final SegmentStatus status, final String source, final String target) {
        return new SegmentView(
                id,
                "title",
                SegmentKind.METADATA_TITLE,
                status,
                source,
                source,
                target,
                null,
                null,
                List.of(),
                null,
                SegmentPath.DRAFT,
                false,
                null,
                null);
    }

    private void scriptTranslatedBook() {
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.ACCEPTED, "Amber Tide", "Бурштиновий Приплив"));
        desk().willAnswerSegment(metadata("aux:creator:0", SegmentStatus.ACCEPTED, "Ann Hale", "Анна Гейл"));
    }

    // IF the rows did not read the stored segments, THEN the model's rendering could not be checked before export.
    @Test
    void card_titleAndAuthorTranslated_rowsShowTheTargetsAndTheOriginals() throws TimeoutException {
        scriptTranslatedBook();
        openFrankensteinThenShowBrief();

        awaitFx(() -> ((TextField) required("brief-book-title")).getText().equals("Бурштиновий Приплив"));

        assertThat(((TextField) required("brief-book-author")).getText()).isEqualTo("Анна Гейл");
        assertThat(((Label) required("brief-book-title-source")).getText()).isEqualTo("Original: Amber Tide");
        assertThat(((TextField) required("brief-book-title")).isDisabled()).isFalse();
    }

    // IF a person could type before the title exists, THEN the edit would have no stored segment to land in.
    @Test
    void card_titleNotTranslatedYet_fieldIsDisabledAndEmptyWithTheHint() throws TimeoutException {
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.PENDING, "Amber Tide", ""));
        openFrankensteinThenShowBrief();

        awaitFx(() -> ((Label) required("brief-book-title-source")).getText().equals("Original: Amber Tide"));

        assertThat(((TextField) required("brief-book-title")).isDisabled()).isTrue();
        assertThat(((TextField) required("brief-book-title")).getText()).isEmpty();
        assertThat(isShown("brief-book-hint")).isTrue();
        assertThat(TooltipProbe.tipText(required("brief-book-title")))
                .contains("written everywhere", "table of contents");
    }

    @Test
    void card_bookWithoutAuthor_authorRowTakesNoRoom() throws TimeoutException {
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.ACCEPTED, "Amber Tide", "Бурштин"));
        openFrankensteinThenShowBrief();

        awaitFx(() -> ((TextField) required("brief-book-title")).getText().equals("Бурштин"));

        assertThat(isShown("brief-book-title")).isTrue();
        assertThat(required("brief-book-author").getParent().isManaged()).isFalse();
    }

    // Node to command: pressing Enter in the field hands the text to the desk as a saved edit of the title segment.
    @Test
    void card_titleEditedAndCommitted_savesAnEditOfTheTitleSegment() throws TimeoutException {
        scriptTranslatedBook();
        openFrankensteinThenShowBrief();
        awaitFx(() -> ((TextField) required("brief-book-title")).getText().equals("Бурштиновий Приплив"));

        onFx(() -> {
            final TextField field = (TextField) required("brief-book-title");
            field.setText("Янтарний Приплив");
            field.fireEvent(new javafx.event.ActionEvent());
        });
        awaitFx(() -> desk().calls().contains("saveEdit(p1, aux:title, Янтарний Приплив)"));

        assertThat(desk().calls()).contains("saveEdit(p1, aux:title, Янтарний Приплив)");
    }
}
