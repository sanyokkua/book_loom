package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The argument list that shows a file in the operating system's file manager.
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
            return List.of("explorer.exe", "/select," + file);
        }
        final Path parent = file.getParent();
        return List.of("xdg-open", (parent != null ? parent : file).toString());
    }
}
