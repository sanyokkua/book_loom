package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.OriginalTitle;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.TitleOrigin;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;

/**
 * The Title and author card on Export: the rows fill from the stored title and author segments once they are translated, are
 * editable only then, and an edit goes to the review desk as a stored edit of that segment.
 */
class ExportTitleCardTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

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

    private void showExport() throws TimeoutException {
        projects.on(EPUB, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(EPUB);
        chooseTarget();
        onFx(() -> shell.activate(ViewNames.EXPORT));
    }

    private void scriptTranslatedBook() {
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.ACCEPTED, "Amber Tide", "Бурштиновий Приплив"));
        desk().willAnswerSegment(metadata("aux:creator:0", SegmentStatus.ACCEPTED, "Ann Hale", "Анна Гейл"));
    }

    // IF the rows did not read the stored segments, THEN the model's rendering could not be checked before export.
    @Test
    void card_titleAndAuthorTranslated_rowsShowTheTargetsAndTheOriginals() throws TimeoutException {
        scriptTranslatedBook();
        showExport();

        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштиновий Приплив"));

        assertThat(((TextField) required("export-book-author")).getText()).isEqualTo("Анна Гейл");
        assertThat(((Label) required("export-book-title-original")).getText()).isEqualTo("Amber Tide");
        assertThat(((Label) required("export-book-author-original")).getText()).isEqualTo("Ann Hale");
        assertThat(((TextField) required("export-book-title")).isDisabled()).isFalse();
    }

    // IF a person could type before the title exists, THEN the edit would have no stored segment to land in.
    @Test
    void card_titleNotTranslatedYet_fieldIsDisabledAndEmptyWithTheHint() throws TimeoutException {
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.PENDING, "Amber Tide", ""));
        showExport();

        awaitFx(() -> ((Label) required("export-book-title-original")).getText().equals("Amber Tide"));

        assertThat(((TextField) required("export-book-title")).isDisabled()).isTrue();
        assertThat(((TextField) required("export-book-title")).getText()).isEmpty();
        assertThat(isShown("export-book-hint")).isTrue();
        assertThat(TooltipProbe.tipText(required("export-book-title")))
                .contains("written everywhere", "table of contents");
    }

    // IF the author row vanished, THEN a person could not tell a book with no author from a card that failed to load.
    @Test
    void card_bookWithoutAuthor_saysTheBookNamesNoAuthor() throws TimeoutException {
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.ACCEPTED, "Amber Tide", "Бурштин"));
        showExport();

        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштин"));

        assertThat(isShown("export-book-author-original")).isTrue();
        assertThat(((Label) required("export-book-author-original")).getText()).isEqualTo("The book names no author");
        assertThat(((TextField) required("export-book-author")).isDisabled()).isTrue();
    }

    // Node to command: pressing Enter in the field hands the text to the desk as a saved edit of the title segment.
    @Test
    void card_titleEditedAndCommitted_savesAnEditOfTheTitleSegment() throws TimeoutException {
        scriptTranslatedBook();
        showExport();
        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштиновий Приплив"));

        onFx(() -> {
            final TextField field = (TextField) required("export-book-title");
            field.setText("Янтарний Приплив");
            field.fireEvent(new javafx.event.ActionEvent());
        });
        awaitFx(() -> desk().calls().contains("saveEdit(p1, aux:title, Янтарний Приплив)"));

        assertThat(desk().calls()).contains("saveEdit(p1, aux:title, Янтарний Приплив)");
    }

    // IF the field carried the Brief's heading as its label, THEN the person would read "Book brief" above a title.
    @Test
    void card_shown_labelsTheFieldsTitleAndAuthorNotBookBrief() throws TimeoutException {
        scriptTranslatedBook();
        showExport();

        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштиновий Приплив"));

        assertThat(textsUnder(required("export-book-card")))
                .contains(
                        "Title and author",
                        "Title · original",
                        "Title · translated",
                        "Author · original",
                        "Author · translated")
                .doesNotContain("Book brief");
    }

    // IF the card sat below Save to, THEN a person would choose the file name before the title it is built from.
    @Test
    void card_shown_sitsAboveSaveTo() throws TimeoutException {
        scriptTranslatedBook();
        showExport();
        awaitFx(() -> ((TextField) required("export-book-title")).getText().equals("Бурштиновий Приплив"));

        final double card = ThemeTestSupport.onFx(
                () -> required("export-book-card").localToScene(0, 0).getY());
        final double saveTo = ThemeTestSupport.onFx(
                () -> required("export-save-to").localToScene(0, 0).getY());

        assertThat(card).isLessThan(saveTo);
    }

    // IF the old hint stayed, THEN Export would send the person to the Brief to edit what it now lets them edit.
    @Test
    void screen_shown_hasNoHintSendingTheTitleToTheBrief() throws TimeoutException {
        showExport();

        assertThat(optional("export-title-hint")).isNull();
    }

    // IF the reason line were missing or worded by the code and not the catalogue, THEN a person could not tell where
    // the title came from.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "DETAILS|From the book's details|aux:title",
                "FRONT_MATTER|From the front matter|aux:fm:title",
                "FIRST_HEADING|From the first heading|ch1:0",
                "FILE_NAME|The book names no title — the file name is used|"
            })
    void card_titleOfEachOrigin_showsItsReasonLine(
            final TitleOrigin origin, final String reason, final @Nullable String segmentId) throws TimeoutException {
        desk().willAnswerOriginalTitle(new OriginalTitle("Amber Tide", origin, segmentId));
        desk().willAnswerSegment(metadata("aux:title", SegmentStatus.ACCEPTED, "Amber Tide", "Бурштин"));
        showExport();

        awaitFx(() -> ((Label) required("export-book-title-reason")).getText().equals(reason));

        assertThat(isShown("export-book-title-reason")).isTrue();
    }

    // IF a file-name title could be typed into, THEN the edit would be lost: no segment holds that title.
    @Test
    void card_titleFromTheFileName_fieldIsDisabledAndEmpty() throws TimeoutException {
        desk().willAnswerOriginalTitle(new OriginalTitle("earth-gravity", TitleOrigin.FILE_NAME, null));
        showExport();

        awaitFx(() -> ((Label) required("export-book-title-original")).getText().equals("earth-gravity"));

        assertThat(((TextField) required("export-book-title")).isDisabled()).isTrue();
        assertThat(((TextField) required("export-book-title")).getText()).isEmpty();
    }

    // IF the author row showed a reason line, THEN it would claim a source the author does not have.
    @Test
    void card_authorRow_hasNoReasonLine() throws TimeoutException {
        scriptTranslatedBook();
        showExport();
        awaitFx(() -> ((TextField) required("export-book-author")).getText().equals("Анна Гейл"));

        assertThat(optional("export-book-author-reason")).isNull();
    }

    private static SegmentView markedHeading() {
        return new SegmentView(
                "ch1:0",
                "ch1:0",
                SegmentKind.HEADING,
                SegmentStatus.ACCEPTED,
                "The ⟦g0⟧Field⟦g1⟧ Notes",
                "The Field Notes",
                "Нотатки ⟦g0⟧Поля⟦g1⟧",
                null,
                null,
                List.of(),
                null,
                SegmentPath.DRAFT,
                false,
                null,
                null);
    }

    private void showMarkedHeading() throws TimeoutException {
        desk().willAnswerOriginalTitle(new OriginalTitle("The Field Notes", TitleOrigin.FIRST_HEADING, "ch1:0"));
        desk().willAnswerSegment(markedHeading());
        showExport();
        awaitFx(() -> ((TextField) required("export-book-title")).getText().contains("⟦0⟧"));
    }

    // IF a heading title hid its marks, THEN a person could delete the formatting without knowing it.
    @Test
    void card_headingTitleWithMarks_showsNumberedMarksInBothFields() throws TimeoutException {
        showMarkedHeading();

        assertThat(((Label) required("export-book-title-original")).getText()).isEqualTo("The ⟦0⟧Field⟦1⟧ Notes");
        assertThat(((TextField) required("export-book-title")).getText()).isEqualTo("Нотатки ⟦0⟧Поля⟦1⟧");
    }

    // IF a mark could be deleted in the field, THEN the gate would refuse the save after the person had left it.
    @Test
    void card_deletingAMarkInTheTitleField_isImpossible() throws TimeoutException {
        showMarkedHeading();
        final TextField field = (TextField) required("export-book-title");

        onFx(() -> field.replaceText(
                field.getText().indexOf("⟦1⟧"), field.getText().indexOf("⟦1⟧") + 3, ""));

        assertThat(field.getText()).contains("⟦1⟧");
    }

    // IF a refused edit said nothing, THEN the person would believe the title was saved.
    @Test
    void card_titleEditTheGateRefuses_showsTheNotSavedMessage() throws TimeoutException {
        showMarkedHeading();
        desk().willAnswer(Result.err(AppError.of(ErrorCode.validation, "Refused", "The placeholders differ.")));

        onFx(() -> {
            final TextField field = (TextField) required("export-book-title");
            field.setText("Записи ⟦0⟧Поля⟦1⟧");
            field.fireEvent(new javafx.event.ActionEvent());
        });
        awaitFx(() -> isShown("export-book-title-refusal"));

        assertThat(labelText("export-book-title-refusal")).startsWith("Not saved:");
    }
}
