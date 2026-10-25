package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A name the save panel returns with its extension twice — the macOS panel adds the filter's extension to a typed name
 * that already ends with it — is written once with it; any other name is kept as it is.
 */
class SaveNamesTest {

    @ParameterizedTest
    @CsvSource({
        "/out/glossary-out.csv.csv, /out/glossary-out.csv",
        "/out/glossary-out.CSV.csv, /out/glossary-out.CSV",
        "/out/Book.uk.epub.EPUB, /out/Book.uk.epub",
        "/out/glossary-out.csv, /out/glossary-out.csv",
        "/out/Book.uk.epub, /out/Book.uk.epub",
        "/out/notes.txt.csv, /out/notes.txt.csv",
        "/out/README, /out/README",
        "/out/.csv.csv, /out/.csv",
    })
    void undoubled_chosenPath_dropsOnlyARepeatedExtension(final String chosen, final String expected) {
        assertThat(SaveNames.undoubled(Path.of(chosen))).isEqualTo(Path.of(expected));
    }
}
