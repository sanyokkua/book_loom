package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

/**
 * An export never replaces a file without asking: when the book file or a chosen side file exists, the person is asked
 * first, every occupied path is named, and declining writes nothing.
 */
class ExportScreenReplaceTest extends ExportScreenTestBase {

    private static final String KEPT = "keep me";

    @TempDir
    private Path dir;

    private Path book;
    private Path glossaryFile;

    @BeforeEach
    void openTheBook() throws TimeoutException {
        book = dir.resolve("Frankenstein.uk.epub");
        glossaryFile = dir.resolve("Frankenstein.uk.glossary.csv");
        openAndShowExport(dir.resolve("Frankenstein.epub"));
        onFx(() -> ((CheckBox) required("export-aux-glossary")).setSelected(true));
        awaitFx(() -> exports().exportAvailable().get());
    }

    private void occupy(final Path... files) throws IOException {
        for (final Path file : files) {
            Files.writeString(file, KEPT);
        }
    }

    private void pressExport() {
        onFx(() -> button("export-run").fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitQuestion() throws TimeoutException {
        awaitFx(() -> optional("confirm-card") != null);
    }

    // IF the question named only the book file, THEN the person would not learn that their glossary is replaced too.
    @Test
    void export_existingDestinationAndGlossary_asksNamingBothPaths() throws IOException, TimeoutException {
        occupy(book, glossaryFile);

        pressExport();
        awaitQuestion();

        assertThat(textOf("confirm-card")).contains(book.toString(), glossaryFile.toString());
        assertThat(textOf("confirm-card")).contains("Replace 2 existing files?");
    }

    // IF Cancel went on to write, THEN a person who declined would lose the file they were protecting.
    @Test
    void export_existingDestinationAndGlossary_cancelWritesNothingAndKeepsBothFiles()
            throws IOException, TimeoutException {
        occupy(book, glossaryFile);
        pressExport();
        awaitQuestion();

        onFx(() -> ((Button) required("confirm-cancel")).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(exportService().requests()).isEmpty();
        assertThat(Files.readString(book)).isEqualTo(KEPT);
        assertThat(Files.readString(glossaryFile)).isEqualTo(KEPT);
    }

    // IF Replace did not allow the overwrite, THEN the export would be refused again after the person agreed.
    @Test
    void export_existingDestination_replaceStartsOneRequestAllowingTheOverwrite() throws IOException, TimeoutException {
        occupy(book);
        pressExport();
        awaitQuestion();

        onFx(() -> ((Button) required("confirm-yes")).fire());
        awaitFx(() -> exportService().requests().size() == 1);

        assertThat(exportService().requests().getFirst().overwrite()).isTrue();
    }

    // IF a chosen side file were not checked, THEN an export could replace a glossary with no question.
    @Test
    void export_onlyTheChosenGlossaryExists_asksNamingJustThatFile() throws IOException, TimeoutException {
        occupy(glossaryFile);

        pressExport();
        awaitQuestion();

        assertThat(textOf("confirm-card")).contains(glossaryFile.toString());
        assertThat(textOf("confirm-card")).doesNotContain(book.toString());
    }

    // IF a free destination still asked, THEN every export would cost a needless question.
    @Test
    void export_nothingExists_startsAtOnceWithoutAskingAndWithoutOverwrite() throws TimeoutException {
        pressExport();
        awaitFx(() -> exportService().requests().size() == 1);

        assertThat(optional("confirm-card")).isNull();
        assertThat(exportService().requests().getFirst().overwrite()).isFalse();
    }

    // IF a side file that is not chosen were checked, THEN an old file nobody would write would block the export.
    @Test
    void export_unchosenGlossaryExists_doesNotAsk() throws IOException, TimeoutException {
        occupy(glossaryFile);
        onFx(() -> ((CheckBox) required("export-aux-glossary")).setSelected(false));

        pressExport();
        awaitFx(() -> exportService().requests().size() == 1);

        assertThat(optional("confirm-card")).isNull();
    }

    // The Replace switch is gone: asking replaces it.
    @Test
    void screen_shown_hasNoReplaceSwitch() {
        assertThat(optional("export-replace")).isNull();
    }
}
