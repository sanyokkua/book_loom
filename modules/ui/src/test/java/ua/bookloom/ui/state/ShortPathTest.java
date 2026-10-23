package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The destination reads as a short path: relative to the home folder when it is inside it, the folder in full when not. */
class ShortPathTest {

    // Built from its parts: a literal home path reads to SpotBugs as a hard-coded file name.
    private static final Path HOME = Path.of("/").resolve("home").resolve("u");

    // IF the whole path were shown, THEN the card would be a line of folders the person never reads.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "/home/u/Books/a.uk.md|Books ▸ a.uk.md|true",
                "/home/u/Books/Ukr/a.uk.md|Books ▸ Ukr ▸ a.uk.md|true",
                "/home/u/a.uk.md|a.uk.md|true",
                "/data/x/a.uk.md|/data/x ▸ a.uk.md|false"
            })
    void of_file_readsRelativeToHomeOrInFullOutsideIt(final String file, final String text, final boolean inHome) {
        final ShortPath shortPath = ShortPath.of(Path.of(file), HOME);

        assertThat(shortPath.text()).isEqualTo(text);
        assertThat(shortPath.inHome()).isEqualTo(inHome);
        assertThat(shortPath.fileName()).isEqualTo("a.uk.md");
    }

    @Test
    void of_fileTwoFoldersDeepInHome_listsTheFoldersBetweenHomeAndTheFile() {
        assertThat(ShortPath.of(HOME.resolve("Books").resolve("Ukr").resolve("a.uk.md"), HOME)
                        .folders())
                .isEqualTo(List.of("Books", "Ukr"));
    }

    @Test
    void of_fileOutsideHome_keepsTheWholeFolderAsOneEntry() {
        assertThat(ShortPath.of(Path.of("/data/x/a.uk.md"), HOME).folders()).isEqualTo(List.of("/data/x"));
    }
}
