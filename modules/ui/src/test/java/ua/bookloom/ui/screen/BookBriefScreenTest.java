package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;

/**
 * The book-brief screen the application builds, read from the real scene: the state with no book open and its way to
 * the import screen, the Languages card (the source the book declares, the target a person chooses), and the two
 * buttons that move around the workflow. The destination is in {@code BookBriefScreenDestinationTest}, the disabled
 * cards in {@code BookBriefScreenDisabledCardsTest}.
 */
class BookBriefScreenTest extends BookBriefScreenTestBase {

    @TempDir
    private Path dir;

    // IF the brief showed an empty form with no book, THEN a person would fill in a language for nothing.
    @Test
    void screen_noBookOpen_reportsItAndPresentsNoTargetDestinationOrOverwrite() {
        showBrief();

        assertThat(isShown("nobook-card")).isTrue();
        assertThat(((Label) required("nobook-report")).getText()).isNotBlank();
        assertThat(optional("brief-target")).isNull();
        assertThat(optional("brief-destination")).isNull();
        assertThat(optional("brief-overwrite")).isNull();
        assertThat(optional("brief-languages-card")).isNull();
    }

    // IF the route to the import screen were not wired, THEN a person on an empty brief would have no way forward.
    @Test
    void nobookOpen_noBookOpen_firingItMovesToTheImportScreen() {
        showBrief();

        onFx(() -> button("nobook-open").fire());

        assertThat(currentView()).isEqualTo(ViewNames.IMPORT);
    }

    // IF the empty report stayed after a book was opened, THEN the brief would say no book is open beside a book.
    @Test
    void screen_bookOpenedThenBriefShownAgain_showsTheBriefAndNotTheReport() throws TimeoutException {
        showBrief();
        assertThat(isShown("nobook-card")).isTrue();

        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        assertThat(isShown("brief-languages-card")).isTrue();
        assertThat(isShown("nobook-card")).isFalse();
        assertThat(optional("brief-target")).isNotNull();
    }

    // IF the source were an editable input, THEN a person could overrule what the book declares with a guess; and
    // IF it were a plain label, THEN it would not be the picker the reference draws for a language.
    @Test
    void source_bookDeclaringEnglish_isADisabledFixedPickerShowingEn() throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        final ComboBox<?> source = (ComboBox<?>) required("brief-source");

        assertThat(source.isDisabled()).isTrue();
        assertThat(source.isEditable()).isFalse();
        assertThat(source.getItems()).hasSize(1);
        assertThat(wordsOf("brief-source")).contains("en");
    }

    // IF a book declaring nothing were shown with a guessed language, THEN the screen would state a fact nobody knows.
    @Test
    void source_bookDeclaringNothing_isADisabledPickerSayingUndeclaredAndShowsNoLanguageCode() throws TimeoutException {
        openBookThenShowBrief(
                dir.resolve("Diary.txt"), BookFixtures.book("diary", BookFormat.TXT, null, null, null, 1));

        final ComboBox<?> source = (ComboBox<?>) required("brief-source");

        assertThat(source.isDisabled()).isTrue();
        assertThat(wordsOf("brief-source")).isNotEmpty().doesNotContain("en", "uk", "pl", "de");
    }

    // IF the target were free text or missing a code, THEN a person could type a language the run cannot use.
    @Test
    void target_bookOpened_isAFixedChoiceOfTheFourCodesDefaultingToUk() throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        final ComboBox<String> target = targetPicker();

        assertThat(target.isEditable()).isFalse();
        assertThat(target.getItems()).containsExactly("uk", "en", "pl", "de");
        assertThat(target.getValue()).isEqualTo("uk");
    }

    // IF a book declaring nothing left the target unusable, THEN plain text could not be given a target; and a target
    // other than the default proves the choice is taken and not just already in place.
    @Test
    void target_bookDeclaringNothing_acceptsAChoice() throws TimeoutException {
        openBookThenShowBrief(
                dir.resolve("Diary.txt"), BookFixtures.book("diary", BookFormat.TXT, null, null, null, 1));

        onFx(() -> briefModel().selectTarget("de"));

        assertThat(targetPicker().getValue()).isEqualTo("de");
        assertThat(chosenTarget()).isEqualTo("de");
    }

    // IF the opening of a book under a brief that is already showing were missed, THEN the report of no book would
    // stay up beside a book, until the person happened to navigate away and back.
    @Test
    void screen_bookOpenedWhileBriefDisplayed_swapsToBriefWithoutNavigating() throws TimeoutException {
        showBrief();
        assertThat(isShown("nobook-card")).isTrue();
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));

        openBook(source);

        assertThat(currentView()).isEqualTo(ViewNames.BOOK_BRIEF);
        assertThat(isShown("brief-languages-card")).isTrue();
        assertThat(isShown("nobook-card")).isFalse();
    }

    // IF choosing in the picker did not reach the view model, THEN the run would use a language the person did not
    // pick.
    @ParameterizedTest
    @ValueSource(strings = {"en", "pl", "de"})
    void target_chosenInThePicker_reachesTheViewModel(final String code) throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        onFx(() -> targetPicker().getSelectionModel().select(code));

        assertThat(chosenTarget()).isEqualTo(code);
    }

    // IF the picker did not follow the view model, THEN it would show one language while the request carried another.
    @ParameterizedTest
    @ValueSource(strings = {"en", "pl", "de"})
    void target_selectedInTheViewModel_isShownInThePicker(final String code) throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        onFx(() -> briefModel().selectTarget(code));

        assertThat(targetPicker().getValue()).isEqualTo(code);
    }

    // IF Back were not wired, THEN a person could not return to the book they opened.
    @Test
    void backControl_bookOpened_firingItMovesToTheImportScreen() throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        onFx(() -> button("brief-back").fire());

        assertThat(currentView()).isEqualTo(ViewNames.IMPORT);
    }

    // IF Continue were not wired to the workflow, THEN a person who has briefed the run could not go on.
    @Test
    void continueControl_bookOpened_firingItMovesToTheStructureScreen() throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        onFx(() -> button("brief-continue").fire());

        assertThat(currentView()).isEqualTo(ViewNames.STRUCTURE);
    }

    // IF the brief were built only for English, THEN a Ukrainian session would show an empty or untranslated report.
    @Test
    void screen_noBookOpenUnderUkrainian_reportsInThatLanguage() {
        useLocale(Locale.forLanguageTag("uk"));
        showBrief();

        assertThat(((Label) required("nobook-report")).getText()).isNotBlank().matches(".*\\p{IsCyrillic}.*");
    }
}
