package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Repairs the name a platform save panel returns: the macOS panel adds its filter's extension to a typed name that
 * already ends with it ("glossary-out.csv" became "glossary-out.csv.csv"), so a repeated last extension is written
 * once.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SaveNames {

    /**
     * Drops a last extension that repeats the one before it, in any letter case.
     *
     * @param chosen the path the save panel returned
     * @return the path with the repeated extension written once, or {@code chosen} itself when nothing repeats
     */
    public static Path undoubled(final Path chosen) {
        Objects.requireNonNull(chosen, "chosen");
        final Path file = chosen.getFileName();
        if (file == null) {
            return chosen;
        }
        final String name = file.toString();
        final int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return chosen;
        }
        final String extension = name.substring(dot).toLowerCase(Locale.ROOT);
        final String stem = name.substring(0, dot);
        final boolean doubled = stem.toLowerCase(Locale.ROOT).endsWith(extension);
        log.debug("save name checked for a repeated extension {}: repeated {}", extension, doubled);
        return doubled ? chosen.resolveSibling(stem) : chosen;
    }
}
