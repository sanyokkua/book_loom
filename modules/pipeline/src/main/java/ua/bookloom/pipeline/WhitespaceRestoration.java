package ua.bookloom.pipeline;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Puts a model's trimmed reply back inside its segment's own leading and trailing whitespace — the book's own
 * spacing wins over whatever a model adds or drops ({@code specs/translation-pipeline/spec.md} "Accept a
 * translation whose markup restores": "The segment's own whitespace wins") — the one place this rule is applied, so
 * the draft step and the quality loop's gate ({@code heal.GateFunction}) never disagree on it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WhitespaceRestoration {

    /**
     * Restores {@code source}'s leading and trailing whitespace around {@code trimmed}.
     *
     * @param source the text whose leading/trailing whitespace wins, e.g. a segment's masked text
     * @param trimmed the candidate text, already stripped of its own leading/trailing whitespace
     * @return {@code source}'s leading whitespace, then {@code trimmed}, then {@code source}'s trailing whitespace
     */
    public static String restore(final String source, final String trimmed) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(trimmed, "trimmed");
        log.debug("Restoring whitespace sourceLength={} trimmedLength={}", source.length(), trimmed.length());
        int leadingEnd = 0;
        while (leadingEnd < source.length() && Character.isWhitespace(source.charAt(leadingEnd))) {
            leadingEnd++;
        }
        int trailingStart = source.length();
        while (trailingStart > leadingEnd && Character.isWhitespace(source.charAt(trailingStart - 1))) {
            trailingStart--;
        }
        final String restored = source.substring(0, leadingEnd) + trimmed + source.substring(trailingStart);
        log.debug(
                "Restored whitespace leadingLength={} trailingLength={} restoredLength={}",
                leadingEnd,
                source.length() - trailingStart,
                restored.length());
        return restored;
    }
}
