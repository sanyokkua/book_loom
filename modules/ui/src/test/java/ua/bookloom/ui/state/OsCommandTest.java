package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The argument list that shows a file in each system's file manager. The function is pure, so every system is driven
 * from one machine; a Windows path is a plain file name to a POSIX {@link Path}, which is enough as only its text is
 * used.
 */
class OsCommandTest {

    private static final Path BOOK = Path.of("books", "Frankenstein.uk.epub");

    // IF macOS were given a folder instead of the file, THEN Finder would open the folder without selecting the book.
    @ParameterizedTest
    @ValueSource(strings = {"Mac OS X", "mac os x", "macOS"})
    void forReveal_macOs_isOpenDashR(final String osName) {
        assertThat(OsCommand.forReveal(osName, BOOK)).containsExactly("open", "-R", BOOK.toString());
    }

    // IF the select switch and the path were two arguments, THEN Explorer would ignore the selection.
    @Test
    void forReveal_windows_isExplorerSelect() {
        final Path written = Path.of("C:\\books\\Frankenstein.uk.epub");

        assertThat(OsCommand.forReveal("Windows 11", written))
                .containsExactly("explorer.exe", "/select,C:\\books\\Frankenstein.uk.epub");
    }

    // IF Linux were given the file, THEN the book would open in whatever program owns its type instead of its folder.
    @Test
    void forReveal_linux_isXdgOpenOfTheParent() {
        assertThat(OsCommand.forReveal("Linux", BOOK)).containsExactly("xdg-open", "books");
    }

    // IF an unrecognised system had no fallback, THEN a BSD or a blank name would get no command at all.
    @ParameterizedTest
    @ValueSource(strings = {"FreeBSD", "", "Solaris"})
    void forReveal_unknownOs_fallsBackToXdgOpen(final String osName) {
        assertThat(OsCommand.forReveal(osName, BOOK)).containsExactly("xdg-open", "books");
    }

    // IF a root path were refused for having no parent, THEN a book written to the root could not be shown.
    @Test
    void forReveal_pathWithNoParent_opensThePathItself() {
        final Path root = Path.of("").toAbsolutePath().getRoot();

        assertThat(OsCommand.forReveal("Linux", root)).containsExactly("xdg-open", root.toString());
    }

    // IF a space or a Cyrillic letter split the path, THEN Finder would be asked for several files that do not exist.
    @Test
    void forReveal_pathWithSpacesAndCyrillic_staysOneArgument() {
        final Path written = Path.of("Мої книги", "Франкенштейн 1.uk.epub");

        assertThat(OsCommand.forReveal("Mac OS X", written)).hasSize(3).last().isEqualTo(written.toString());
    }
}
