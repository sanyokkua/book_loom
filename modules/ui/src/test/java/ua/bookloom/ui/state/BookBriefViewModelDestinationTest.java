package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;

/**
 * Where the translation is proposed to be written and when a proposal gives way to a path a person chose: the
 * default follows the source and the target, an edited path is left alone, and opening a different book starts over.
 * The suffix itself is the shared helper's answer, so these tests pin only what the view model does with it.
 */
class BookBriefViewModelDestinationTest extends BookBriefViewModelTestBase {

    private static final Path FRANKENSTEIN = Path.of("/books/Frankenstein.epub");

    // IF the view model proposed nothing or used another target, THEN the person would start from a wrong path.
    @Test
    void destination_epubOpenedWithDefaultTarget_isProposedBesideTheSourceWithUk() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        assertThat(target()).isEqualTo("uk");
        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.epub").toString());
    }

    // IF the target were not part of the proposal, THEN every language would be written to the same file.
    @Test
    void destination_targetChangedToGerman_proposalMovesToTheGermanName() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        selectTarget("de");

        assertThat(target()).isEqualTo("de");
        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.de.epub").toString());
    }

    // IF a target change overwrote a path typed by hand, THEN a person's choice would silently be undone.
    @Test
    void destination_editedByHandThenTargetChanged_staysWhereThePersonPutIt() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());
        editDestination("/archive/out.epub");

        selectTarget("de");

        assertThat(destination()).isEqualTo("/archive/out.epub");
    }

    // IF a path picked in the file chooser were not counted as edited, THEN the next target change would discard it.
    @Test
    void destination_chosenInTheFileChooserThenTargetChanged_staysWhereThePersonPutIt() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());
        chooseDestination(Path.of("/archive/out.epub"));

        selectTarget("pl");

        assertThat(destination()).isEqualTo(Path.of("/archive/out.epub").toString());
    }

    // IF the composite .fb2.zip suffix were split at the last dot, THEN the output would be Kobzar.fb2.uk.zip.
    @Test
    void destination_fb2ZipOpened_keepsTheCompositeSuffixWhole() {
        openBook(
                Path.of("/books/Kobzar.fb2.zip"),
                BookFixtures.book("kobzar", BookFormat.FB2, "uk", "Kobzar", "Shevchenko", 1));

        assertThat(destination()).isEqualTo(Path.of("/books/Kobzar.uk.fb2.zip").toString());
    }

    // IF the view model built the suffix itself, THEN each format's naming would be a second copy of the shared rule.
    @ParameterizedTest
    @CsvSource({
        "/notes/Diary.txt, TXT, /notes/Diary.uk.txt",
        "/notes/Readme.md, MARKDOWN, /notes/Readme.uk.md",
        "/notes/Readme.markdown, MARKDOWN, /notes/Readme.uk.markdown"
    })
    void destination_eachFormatOpened_followsThatFormatsOwnSuffix(
            final String source, final BookFormat format, final String expected) {
        openBook(Path.of(source), BookFixtures.book("any", format, null, null, null, 1));

        assertThat(destination()).isEqualTo(Path.of(expected).toString());
    }

    // IF the hand-edited flag outlived the book, THEN a second book would be proposed no path at all.
    @Test
    void destination_aDifferentBookOpenedAfterAnEdit_isProposedAgain() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());
        editDestination("/archive/out.epub");

        openBook(Path.of("/other/Dracula.epub"), BookFixtures.book("dracula", BookFormat.EPUB, "en", null, null, 1));

        assertThat(destination()).isEqualTo(Path.of("/other/Dracula.uk.epub").toString());
    }

    // IF the reset were not complete, THEN after a second book the target change would still be ignored.
    @Test
    void destination_aDifferentBookAfterAnEdit_followsTheTargetAgain() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());
        editDestination("/archive/out.epub");
        openBook(Path.of("/other/Dracula.epub"), BookFixtures.book("dracula", BookFormat.EPUB, "en", null, null, 1));

        selectTarget("de");

        assertThat(destination()).isEqualTo(Path.of("/other/Dracula.de.epub").toString());
    }

    // IF the screen's first visit came after the open, THEN a view model created late would show no destination.
    @Test
    void destination_viewModelCreatedAfterTheBookWasOpened_proposesForThatBook() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        recreateBrief();

        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.epub").toString());
    }

    // IF a destination were shown with nothing open, THEN the person would see a path for no book.
    @Test
    void destination_noBookOpen_isEmpty() {
        assertThat(destination()).isEmpty();
    }

    // IF the target were not one of the four offered codes, THEN the picker and the request would disagree.
    @Test
    void targetLanguages_theOfferedCodes_areUkEnPlDeInThatOrder() {
        assertThat(BookBriefViewModel.TARGET_LANGUAGES).containsExactly("uk", "en", "pl", "de");
    }

    // IF the default target were empty or another code, THEN a run could start toward no language.
    @Test
    void targetLanguage_beforeAnyChoice_isUkrainian() {
        assertThat(target()).isEqualTo("uk");
    }

    // IF the book's own language were copied into the target, THEN a person would translate into the source language.
    @Test
    void targetLanguage_aBookDeclaringEnglishOpened_staysUkrainian() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        assertThat(target()).isEqualTo("uk");
    }
}
