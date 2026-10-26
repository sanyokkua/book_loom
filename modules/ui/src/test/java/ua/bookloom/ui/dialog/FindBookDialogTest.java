package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.state.ProjectRow;

/** The Find the book card: what it says for a missing and a changed file, its error line and its way out. */
class FindBookDialogTest extends ShellTestBase {

    private static final Project PROJECT = ProjectFixtures.project(
            "p-uk",
            ProjectFixtures.BOOK_FILE,
            ProjectFixtures.BOOK_HASH,
            "en",
            "uk",
            NameOrigin.USER,
            "Earth Gravity · my run",
            Instant.parse("2026-10-21T10:00:00Z"));
    private static final ProjectRow ROW = ProjectFixtures.row(PROJECT, 0, null);

    private final List<String> answers = new ArrayList<>();

    private void ask(final ProjectOpening opening, final @Nullable String error) {
        onFx(() -> injector.getInstance(FindBookDialog.class)
                .ask(ROW, opening, error, () -> answers.add("choose"), () -> answers.add("cancelled")));
    }

    @Test
    void ask_missingFile_saysTheFileIsNotWhereItWasAndOffersFindTheBookPrimary() {
        ask(new ProjectOpening.BookFileMissing(PROJECT), null);

        assertThat(textsUnder(required("findbook-card")))
                .contains(
                        "Find the book",
                        "The book file of Earth Gravity · my run is not where it was. Choose the same book to go on.",
                        "Find the book...",
                        "Cancel");
        assertThat(((Button) required("findbook-choose")).getStyleClass()).contains("btn-primary");
        assertThat(isVisibleInScene("findbook-error")).isFalse();
    }

    @Test
    void ask_changedFile_saysTheFileChangedSinceImport() {
        ask(new ProjectOpening.BookFileChanged(PROJECT), null);

        assertThat(textsUnder(required("findbook-card")))
                .contains(
                        "The book file of Earth Gravity · my run has changed since it was imported. Choose the same book to go on.");
    }

    // IF the error line were missing, THEN a refused different book would look like the card simply ignored the file.
    @Test
    void ask_withAnError_showsItInTheErrorLine() {
        ask(new ProjectOpening.BookFileMissing(PROJECT), "This is a different book");

        assertThat(isVisibleInScene("findbook-error")).isTrue();
        assertThat(((Label) required("findbook-error")).getText()).isEqualTo("This is a different book");
    }

    @Test
    void chooseButton_pressed_runsChoose() {
        ask(new ProjectOpening.BookFileMissing(PROJECT), null);

        onFx(() -> ((Button) required("findbook-choose")).fire());

        assertThat(answers).containsExactly("choose");
    }

    @ParameterizedTest
    @ValueSource(strings = {"button", "escape"})
    void cancelOrEscape_leavesTheProjectClosedAndRunsCancelled(final String how) {
        ask(new ProjectOpening.BookFileMissing(PROJECT), null);

        onFx(() -> leave(how));

        assertThat(answers).containsExactly("cancelled");
        assertThat(isVisibleInScene("findbook-card")).isFalse();
    }

    private void leave(final String how) {
        if ("button".equals(how)) {
            ((Button) required("findbook-cancel")).fire();
        } else {
            javafx.event.Event.fireEvent(
                    scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
        }
    }

    @Test
    void ask_underUkrainian_speaksUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));

        ask(new ProjectOpening.BookFileMissing(PROJECT), null);

        assertThat(textsUnder(required("findbook-card")))
                .contains(
                        "Знайти книгу",
                        "Файлу книги проєкту Earth Gravity · my run немає на колишньому місці. Виберіть ту саму книгу, щоб продовжити.",
                        "Знайти книгу...");
    }
}
