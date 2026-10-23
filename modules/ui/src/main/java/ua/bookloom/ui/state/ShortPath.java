package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A destination as a person reads it: the folders below the home folder and the file name, or the whole folder as one
 * entry when the file lies outside the home folder. The full path stays on hover.
 *
 * @param folders the folders between the home folder and the file, or the file's whole folder when it is outside home;
 *     empty for a file right in the home folder
 * @param fileName the file's own name
 * @param inHome whether the file lies inside the home folder
 */
public record ShortPath(List<String> folders, String fileName, boolean inHome) {

    /** The separator between the parts of a short path. */
    public static final String SEPARATOR = " ▸ ";

    /** Copies the folders and rejects a missing name. */
    public ShortPath {
        folders = List.copyOf(Objects.requireNonNull(folders, "folders"));
        Objects.requireNonNull(fileName, "fileName");
    }

    /**
     * The short form of {@code file} against {@code home}.
     *
     * @param file the destination; an absolute path with a file name
     * @param home the person's home folder
     * @return never null
     */
    public static ShortPath of(final Path file, final Path home) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(home, "home");
        final Path normalized = file.toAbsolutePath().normalize();
        final Path name = normalized.getFileName();
        final String fileName = name == null ? normalized.toString() : name.toString();
        final Path parent = normalized.getParent();
        final Path homeFolder = home.toAbsolutePath().normalize();
        if (parent != null && parent.startsWith(homeFolder)) {
            final List<String> below = new ArrayList<>();
            homeFolder.relativize(parent).forEach(part -> addPart(below, part.toString()));
            return new ShortPath(below, fileName, true);
        }
        return new ShortPath(parent == null ? List.of() : List.of(parent.toString()), fileName, false);
    }

    private static void addPart(final List<String> parts, final String part) {
        if (!part.isEmpty()) {
            parts.add(part);
        }
    }

    /**
     * The short path as one line.
     *
     * @return the folders and the file name joined by {@link #SEPARATOR}
     */
    public String text() {
        final List<String> parts = new ArrayList<>(folders);
        parts.add(fileName);
        return String.join(SEPARATOR, parts);
    }
}
