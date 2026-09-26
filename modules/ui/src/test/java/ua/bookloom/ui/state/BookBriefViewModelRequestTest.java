package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.BookFixtures;

/**
 * What the brief hands to a run and what it reports before one: the source language the book declares, the
 * occupied-destination report, and the assembled request with its refusals to assemble one from nothing sensible.
 */
class BookBriefViewModelRequestTest extends BookBriefViewModelTestBase {

    private static final Path FRANKENSTEIN = Path.of("/books/Frankenstein.epub");

    @TempDir
    private Path dir;

    // IF the declared language were not surfaced, THEN the source could not be shown as the book says it is.
    @Test
    void sourceLanguage_bookDeclaringEnglish_isPresent() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        assertThat(sourceLanguage()).hasValue("en");
    }

    // IF a book declaring nothing produced a guess, THEN the person would be shown a language nobody stated.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void sourceLanguage_bookDeclaringNothingOrBlank_isEmpty(final String declared) {
        openBook(Path.of("/notes/Diary.txt"), BookFixtures.book("diary", BookFormat.TXT, declared, null, null, 1));

        assertThat(sourceLanguage()).isEmpty();
    }

    // IF a book with no declared language blocked the choice of a target, THEN plain text could not be translated; and
    // a target other than the default proves the choice is taken and not just already in place.
    @Test
    void selectTarget_bookDeclaringNothing_stillAcceptsAChoice() {
        openBook(Path.of("/notes/Diary.txt"), BookFixtures.book("diary", BookFormat.TXT, null, null, null, 1));

        selectTarget("de");

        assertThat(target()).isEqualTo("de");
        assertThat(sourceLanguage()).isEmpty();
    }

    // IF a source language were reported with no book, THEN the screen would name a language for nothing.
    @Test
    void sourceLanguage_noBookOpen_isEmpty() {
        assertThat(sourceLanguage()).isEmpty();
    }

    // IF the view model expected the file to appear later, THEN an occupied path would be found only at run time.
    @Test
    void destinationExists_fileAtTheDestinationAndOverwriteOff_isTrue() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        assertThat(destination()).isEqualTo(occupied.toString());
        assertThat(destinationExists()).isTrue();
    }

    // IF permission to replace did not silence the report, THEN a person who allowed it would still be warned.
    @Test
    void destinationExists_fileAtTheDestinationAndOverwriteOn_isFalse() throws IOException {
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        setOverwrite(true);

        assertThat(overwrite()).isTrue();
        assertThat(destinationExists()).isFalse();
    }

    // IF permission to replace outlived the book it was given for, THEN opening another book would let a run replace
    // a file nobody agreed to lose, and would hide the warning about it.
    @Test
    void overwrite_differentBookOpenedAfterReplaceAllowed_isOffAndWarningShown() throws IOException {
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        setOverwrite(true);
        Files.writeString(dir.resolve("Dracula.uk.epub"), "already here too");

        openBook(dir.resolve("Dracula.epub"), BookFixtures.book("dracula", BookFormat.EPUB, "en", null, null, 1));

        assertThat(overwrite()).isFalse();
        assertThat(destinationExists()).isTrue();
    }

    // IF the report were only recomputed on a change, THEN a file that appeared since the last look would go
    // unreported when the brief is shown again.
    @Test
    void refresh_fileAppearedAfterTheLastCheck_reportsIt() throws IOException {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        assertThat(destinationExists()).isFalse();
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "appeared later");

        refresh();

        assertThat(destinationExists()).isTrue();
    }

    // IF the report were kept when the file went away, THEN the warning would describe a file that is gone.
    @Test
    void refresh_fileRemovedAfterTheLastCheck_clearsTheReport() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        assertThat(destinationExists()).isTrue();
        Files.delete(occupied);

        refresh();

        assertThat(destinationExists()).isFalse();
    }

    // IF switching overwrite off again did not bring the report back, THEN the warning would be stale.
    @Test
    void destinationExists_overwriteSwitchedOnThenOff_isTrueAgain() throws IOException {
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        setOverwrite(true);

        setOverwrite(false);

        assertThat(destinationExists()).isTrue();
    }

    // IF a typed destination were not rechecked, THEN the report would describe the previous path.
    @Test
    void destinationExists_destinationEditedToAnExistingFile_isTrue() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        assertThat(destinationExists()).isFalse();

        editDestination(occupied.toString());

        assertThat(destinationExists()).isTrue();
    }

    // IF a path picked in the chooser were not rechecked, THEN the report would describe the previous path.
    @Test
    void destinationExists_destinationChosenAsAnExistingFile_isTrue() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        chooseDestination(occupied);

        assertThat(destinationExists()).isTrue();
    }

    // IF a free path were reported as occupied, THEN the warning would cry wolf on every book.
    @Test
    void destinationExists_noFileAtTheDestination_isFalse() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());

        assertThat(destinationExists()).isFalse();
    }

    // IF overwrite started on, THEN replacing a file would be a default and not a choice.
    @Test
    void overwrite_beforeAnyChoice_isOff() {
        assertThat(overwrite()).isFalse();
    }

    // IF the request omitted or invented a component, THEN the run would read something the person never chose.
    @Test
    void request_bookOpenedAndTargetChosen_carriesSourceDestinationTargetAndOverwriteOnly() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());
        selectTarget("de");
        setOverwrite(true);

        assertThat(request())
                .hasValue(
                        new TranslationRequest(FRANKENSTEIN, Path.of("/books/Frankenstein.de.epub"), "de", null, true));
    }

    // IF the request carried the declared language, THEN a later detection could not be told from a person's choice.
    @Test
    void request_bookDeclaringEnglish_leavesTheSourceLanguageNull() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        assertThat(request())
                .hasValueSatisfying(r -> assertThat(r.sourceLanguage()).isNull());
    }

    // IF a hand-edited destination did not reach the request, THEN the run would write somewhere else.
    @Test
    void request_destinationEditedByHand_usesTheEditedPath() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());
        editDestination("/archive/out.epub");

        assertThat(request())
                .hasValueSatisfying(r -> assertThat(r.destination()).isEqualTo(Path.of("/archive/out.epub")));
    }

    // IF a request could be built with no book, THEN a run would start on nothing.
    @Test
    void request_noBookOpen_isEmpty() {
        assertThat(request()).isEmpty();
    }

    // IF a blank destination produced a request, THEN the run would fail late on a path that names nothing.
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void request_blankDestination_isEmpty(final String blank) {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        editDestination(blank);

        assertThat(request()).isEmpty();
    }

    // IF an unparsable path threw, THEN typing one character would crash the screen instead of just disabling Continue.
    @Test
    void request_destinationWithANulCharacter_isEmptyAndDoesNotThrow() {
        openBook(FRANKENSTEIN, BookFixtures.frankenstein());

        assertThatCode(() -> editDestination("/books/bad\0name.epub")).doesNotThrowAnyException();

        assertThat(request()).isEmpty();
        assertThat(destinationExists()).isFalse();
    }
}
