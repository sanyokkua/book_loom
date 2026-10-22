package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.BookFixtures;

/**
 * Where the translation is proposed to be written and when a proposal gives way to a path a person chose: the
 * default follows the source and the target, an edited path is left alone, and opening a different book starts over.
 * The suffix itself is the shared helper's answer, so these tests pin only what the view model does with it.
 */
class ExportViewModelDestinationTest extends ExportViewModelTestBase {

    private static final Path FRANKENSTEIN = Path.of("/books/Frankenstein.epub");

    // IF the view model proposed nothing or used another target, THEN the person would start from a wrong path.
    @Test
    void destination_epubOpenedWithTargetUk_isProposedBesideTheSourceWithUk() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());

        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.epub").toString());
    }

    // IF the target were not part of the proposal, THEN every language would be written to the same file.
    @Test
    void destination_targetChangedToGerman_proposalMovesToTheGermanName() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());

        selectTarget("de");

        assertThat(target()).isEqualTo("de");
        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.de.epub").toString());
    }

    // IF a target change overwrote a path typed by hand, THEN a person's choice would silently be undone.
    @Test
    void destination_editedByHandThenTargetChanged_staysWhereThePersonPutIt() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        editDestination("/archive/out.epub");

        selectTarget("de");

        assertThat(destination()).isEqualTo("/archive/out.epub");
    }

    // IF a path picked in the file chooser were not counted as edited, THEN the next target change would discard it.
    @Test
    void destination_chosenInTheFileChooserThenTargetChanged_staysWhereThePersonPutIt() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        chooseDestination(Path.of("/archive/out.epub"));

        selectTarget("pl");

        assertThat(destination()).isEqualTo(Path.of("/archive/out.epub").toString());
    }

    // IF the composite .fb2.zip suffix were split at the last dot, THEN the output would be Kobzar.fb2.uk.zip.
    @Test
    void destination_fb2ZipOpened_keepsTheCompositeSuffixWhole() {
        openBook(
                Path.of("/books/Kobzar.fb2.zip"),
                BookFixtures.imported("kobzar", BookFormat.FB2, "uk", "Kobzar", "Shevchenko", 1));

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
        openBook(Path.of(source), BookFixtures.imported("any", format, null, null, null, 1));

        assertThat(destination()).isEqualTo(Path.of(expected).toString());
    }

    // IF the hand-edited flag outlived the book, THEN a second book would be proposed no path at all.
    @Test
    void destination_aDifferentBookOpenedAfterAnEdit_isProposedAgain() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        editDestination("/archive/out.epub");

        openBook(
                Path.of("/other/Dracula.epub"), BookFixtures.imported("dracula", BookFormat.EPUB, "en", null, null, 1));

        assertThat(destination()).isEqualTo(Path.of("/other/Dracula.uk.epub").toString());
    }

    // IF the reset were not complete, THEN after a second book the target change would still be ignored.
    @Test
    void destination_aDifferentBookAfterAnEdit_followsTheTargetAgain() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        editDestination("/archive/out.epub");
        openBook(
                Path.of("/other/Dracula.epub"), BookFixtures.imported("dracula", BookFormat.EPUB, "en", null, null, 1));

        selectTarget("de");

        assertThat(destination()).isEqualTo(Path.of("/other/Dracula.de.epub").toString());
    }

    // IF the screen's first visit came after the open, THEN a view model created late would show no destination.
    @Test
    void destination_viewModelCreatedAfterTheBookWasOpened_proposesForThatBook() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());

        recreateExports();

        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.epub").toString());
    }

    // IF a destination were shown with nothing open, THEN the person would see a path for no book.
    @Test
    void destination_noBookOpen_isEmpty() {
        assertThat(destination()).isEmpty();
    }

    // IF a destination were proposed before any target is chosen, THEN the file name would carry a language nobody
    // chose.
    @Test
    void destination_bookOpenedWithNoTargetChosen_isEmptyUntilOneIs() {
        openBookOnly(FRANKENSTEIN, BookFixtures.frankensteinImport());
        assertThat(destination()).isEmpty();

        selectTarget("pl");

        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.pl.epub").toString());
    }

    // IF the book's own language were copied into the target, THEN a person would translate into the source language.
    @Test
    void target_aBookDeclaringEnglishOpened_isNotChosenForThePerson() {
        openBookOnly(FRANKENSTEIN, BookFixtures.frankensteinImport());

        assertThat(current.brief().get())
                .isNotNull()
                .extracting(b -> b.targetLanguage())
                .isNull();
    }

    // IF Export book were dimmed with nothing said, THEN a person with an empty Save to would not know what it waits
    // for; the note goes once a path is there.
    @Test
    void currentNote_bookOpenWithNoDestination_saysToChooseWhereToSave() {
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        editDestination("");
        final String empty = onFx(() -> exports.currentNote().get());

        editDestination("/archive/out.epub");

        assertThat(empty).isEqualTo("Choose where to write the book: type a path in Save to or press Browse.");
        assertThat(onFx(() -> exports.currentNote().get())).isEmpty();
    }

    private static ProjectSummary summary(final String id, final String name, final NameOrigin origin) {
        return new ProjectSummary(
                new Project(
                        id,
                        FRANKENSTEIN,
                        BookFormat.EPUB,
                        "hash-1",
                        BookBrief.defaults("en").withLanguages("en", "uk"),
                        name,
                        origin,
                        "Frankenstein",
                        Instant.EPOCH,
                        Instant.EPOCH),
                0,
                0,
                null,
                null);
    }

    private void listTwoProjectsOfOnePair() {
        projects.willList(List.of(
                summary("auto", "Frankenstein · EN → UK", NameOrigin.AUTO),
                summary("p1", "Frankenstein · 26b max", NameOrigin.USER)));
    }

    // IF the label were not asked of the stored projects, THEN a second translation of one pair would replace the
    // first.
    @Test
    void destination_namedProjectSharingItsPair_gainsTheLabelOnceTheListArrives() {
        listTwoProjectsOfOnePair();
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());

        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.26b-max.epub").toString());
    }

    // IF the first proposal waited for the list, THEN the field would be empty while the stored projects are read.
    @Test
    void destination_listNotAnsweredYet_isProposedWithoutALabelThenLabelledOnArrival() {
        listTwoProjectsOfOnePair();
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        final ManualExecutor manual = new ManualExecutor();
        exports = onFx(() -> newExports(manual));
        WaitForAsyncUtils.waitForFxEvents();
        final String before = destination();

        manual.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(before).isEqualTo(Path.of("/books/Frankenstein.uk.epub").toString());
        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.26b-max.epub").toString());
    }

    // IF the late label overwrote a path typed meanwhile, THEN a person's choice would silently be undone.
    @Test
    void destination_editedByHandBeforeTheListArrives_isNotOverwrittenByTheLabel() {
        listTwoProjectsOfOnePair();
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());
        final ManualExecutor manual = new ManualExecutor();
        exports = onFx(() -> newExports(manual));
        WaitForAsyncUtils.waitForFxEvents();
        editDestination("/archive/out.epub");

        manual.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(destination()).isEqualTo("/archive/out.epub");
    }

    // IF a project alone with its pair were labelled, THEN its file would differ from the command line's.
    @Test
    void destination_namedProjectAloneWithItsPair_hasNoLabel() {
        projects.willList(List.of(summary("p1", "Frankenstein · 26b max", NameOrigin.USER)));
        openBook(FRANKENSTEIN, BookFixtures.frankensteinImport());

        assertThat(destination())
                .isEqualTo(Path.of("/books/Frankenstein.uk.epub").toString());
    }
}
