package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The argument lists that show or open a file through the operating system, and the reading of their exit codes.
 *
 * <p>Pure so that each system can be tested from one machine: it takes the {@code os.name} text rather than reading
 * it, and it only uses the path's text, so a Windows path is testable on a POSIX JVM.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OsCommand {

    private static final String MAC_PREFIX = "mac";
    private static final String WINDOWS_PREFIX = "windows";
    private static final String EXPLORER = "explorer.exe";
    // Explorer reports 1 even when it showed the file, so that code is its success on Windows.
    private static final int EXPLORER_SHOWN = 1;

    /**
     * Builds the command that shows {@code file}: it selects the file in Finder and in Explorer, which can, and opens
     * the file's folder anywhere else, where there is no portable way to select a file.
     *
     * <p>Each path is one list element, so a space or a non-ASCII letter needs no quoting and no shell reads it. Windows
     * gets the switch and the path as a single element because Explorer parses {@code /select,<path>} as one argument.
     *
     * @param osName the {@code os.name} value; a blank or unrecognised name is treated as Linux
     * @param file the written book; a path with no parent, such as a root, is opened itself
     * @return the program followed by its arguments; never empty
     */
    static List<String> forReveal(final String osName, final Path file) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(file, "file");
        final String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(MAC_PREFIX)) {
            return List.of("open", "-R", file.toString());
        }
        if (normalized.startsWith(WINDOWS_PREFIX)) {
            return List.of(EXPLORER, "/select," + file);
        }
        final Path parent = file.getParent();
        return List.of("xdg-open", (parent != null ? parent : file).toString());
    }

    /**
     * Builds the command that opens {@code file} in the program the system associates with its type.
     *
     * @param osName the {@code os.name} value; a blank or unrecognised name is treated as Linux
     * @param file the written book
     * @return the program followed by its arguments; never empty
     */
    static List<String> forOpen(final String osName, final Path file) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(file, "file");
        final String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(MAC_PREFIX)) {
            return List.of("open", file.toString());
        }
        if (normalized.startsWith(WINDOWS_PREFIX)) {
            return List.of(EXPLORER, file.toString());
        }
        return List.of("xdg-open", file.toString());
    }

    /**
     * Decides whether a finished command did what it was asked.
     *
     * @param osName the {@code os.name} value the command was built for
     * @param program the command's first element
     * @param exitCode the process's exit code
     * @return {@code true} for exit 0 anywhere and for exit 1 from {@code explorer.exe} on Windows, {@code false}
     *     otherwise
     */
    static boolean succeeded(final String osName, final String program, final int exitCode) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(program, "program");
        if (exitCode == 0) {
            return true;
        }
        return exitCode == EXPLORER_SHOWN
                && EXPLORER.equals(program)
                && osName.toLowerCase(Locale.ROOT).startsWith(WINDOWS_PREFIX);
    }
}
