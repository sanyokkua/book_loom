package ua.bookloom.util.paths;

import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The three directory conventions BookLoom targets, selected from {@code os.name}.
 *
 * <p>Carries its own path separator and its own notion of "absolute" rather than using {@link java.io.File#separator}
 * or {@link java.nio.file.Path#isAbsolute()}. That is the whole reason the per-OS matrix is testable on one machine:
 * on a macOS JVM {@code Path.of("C:\\Users\\x")} is a <em>relative</em> path, so a resolver that reasoned in
 * {@code Path} could only ever be tested against the host it happens to run on.
 */
enum OsFamily {

    /** Windows: {@code %LOCALAPPDATA%}, backslash-separated, drive-letter or UNC absolute paths. */
    WINDOWS('\\'),

    /** macOS: {@code ~/Library/Application Support} and {@code ~/Library/Logs}. */
    MACOS('/'),

    /** Linux and other Unixes: the XDG base directories. */
    LINUX('/');

    private final char separator;

    OsFamily(char separator) {
        this.separator = separator;
    }

    /**
     * Selects the family from an {@code os.name} value.
     *
     * <p>An unknown, blank or absent value resolves to {@link #LINUX}: the XDG layout is the reasonable default for
     * any remaining Unix, and guessing Windows on an unrecognised system would put the database behind a
     * drive-letter path that cannot exist there.
     */
    static OsFamily from(Function<String, @Nullable String> getProperty) {
        final String osName = getProperty.apply("os.name");
        if (osName == null) {
            return LINUX;
        }
        final String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("windows")) {
            return WINDOWS;
        }
        return normalized.startsWith("mac") ? MACOS : LINUX;
    }

    /** Joins path segments with this family's separator. */
    String join(String base, String... segments) {
        final StringBuilder joined = new StringBuilder(stripTrailingSeparator(base));
        for (final String segment : segments) {
            joined.append(separator).append(segment);
        }
        return joined.toString();
    }

    /**
     * Whether a raw configured value is an absolute path <em>for this family</em>.
     *
     * <p>Blank and relative values are rejected together because the specification treats them the same way: a blank
     * {@code XDG_DATA_HOME} and a relative one are both "ignored, fall back" (EC-ENV-1), and a relative
     * {@code BOOKLOOM_DATA_DIR} is ignored rather than resolved against the working directory (EC-ENV-9).
     */
    boolean isAbsolute(@Nullable String value) {
        if (value == null) {
            return false;
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (this == WINDOWS) {
            final boolean unc = trimmed.startsWith("\\\\") || trimmed.startsWith("//");
            final boolean driveLetter = trimmed.length() >= 3
                    && Character.isLetter(trimmed.charAt(0))
                    && trimmed.charAt(1) == ':'
                    && (trimmed.charAt(2) == '\\' || trimmed.charAt(2) == '/');
            return unc || driveLetter;
        }
        return trimmed.startsWith("/");
    }

    private String stripTrailingSeparator(String base) {
        final String trimmed = base.trim();
        final int end = trimmed.length();
        if (end > 1 && (trimmed.charAt(end - 1) == '\\' || trimmed.charAt(end - 1) == '/')) {
            return trimmed.substring(0, end - 1);
        }
        return trimmed;
    }
}
