package ua.bookloom.pipeline.qa;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.util.lang.Script;

/**
 * A text's dominant script, by letter count — the signal {@link ForeignMarking} uses to tell a genuinely foreign
 * passage from one merely written in the source language.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DominantScript {

    /**
     * The {@link Script} whose letters occur most often in {@code text}.
     *
     * @param text the text to scan
     * @param tieBreak the script to return when it ties for the most letters, when two or more scripts tie with
     *     neither being {@code tieBreak}, or when {@code text} has no letter of any catalogued script — Han and
     *     Japanese share Han letters, so a Chinese passage in an English book ties CJK against Latin and this
     *     resolves it toward the source language's own script
     * @return the dominant script, or {@code tieBreak} per the tie rule above
     */
    static Script of(final String text, final Script tieBreak) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(tieBreak, "tieBreak");
        final Map<Script, Long> counts = countLettersByScript(text);
        if (counts.isEmpty()) {
            return tieBreak;
        }
        final long max = Math.max(
                counts.values().stream().mapToLong(Long::longValue).max().orElseThrow(), 0L);
        if (counts.getOrDefault(tieBreak, 0L) == max) {
            return tieBreak;
        }
        return Arrays.stream(Script.values())
                .filter(script -> script != Script.UNKNOWN && counts.getOrDefault(script, 0L) == max)
                .findFirst()
                .orElseThrow();
    }

    private static Map<Script, Long> countLettersByScript(final String text) {
        final Map<Script, Long> counts = new EnumMap<>(Script.class);
        text.codePoints().filter(Character::isLetter).forEach(codePoint -> tally(counts, codePoint));
        return counts;
    }

    private static void tally(final Map<Script, Long> counts, final int codePoint) {
        final Character.UnicodeScript unicodeScript = Character.UnicodeScript.of(codePoint);
        for (final Script script : Script.values()) {
            if (script != Script.UNKNOWN && script.letterScripts().contains(unicodeScript)) {
                counts.merge(script, 1L, Long::sum);
            }
        }
    }
}
