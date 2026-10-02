package ua.bookloom.pipeline;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes the pseudo model's capitals in Cyrillic, leaving placeholders and entities alone, so a clean pseudo reply
 * reads as a Ukrainian target to the script and echo checks while staying deterministic.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Cyrillic {

    private static final Pattern PROTECTED =
            Pattern.compile("⟦g\\d+⟧|&(?:#(?:x|X)[0-9A-Fa-f]+|#\\d+|[A-Za-z][A-Za-z0-9]+);");
    private static final Map<Character, String> LETTERS = Map.ofEntries(
            Map.entry('A', "А"),
            Map.entry('B', "Б"),
            Map.entry('C', "Ц"),
            Map.entry('D', "Д"),
            Map.entry('E', "Е"),
            Map.entry('F', "Ф"),
            Map.entry('G', "Г"),
            Map.entry('H', "Х"),
            Map.entry('I', "І"),
            Map.entry('J', "Й"),
            Map.entry('K', "К"),
            Map.entry('L', "Л"),
            Map.entry('M', "М"),
            Map.entry('N', "Н"),
            Map.entry('O', "О"),
            Map.entry('P', "П"),
            Map.entry('Q', "К"),
            Map.entry('R', "Р"),
            Map.entry('S', "С"),
            Map.entry('T', "Т"),
            Map.entry('U', "У"),
            Map.entry('V', "В"),
            Map.entry('W', "В"),
            Map.entry('X', "КС"),
            Map.entry('Y', "И"),
            Map.entry('Z', "З"));

    static String of(final String text) {
        final Matcher matcher = PROTECTED.matcher(text);
        final StringBuilder out = new StringBuilder(text.length());
        int from = 0;
        while (matcher.find()) {
            letters(out, text.substring(from, matcher.start()));
            out.append(matcher.group());
            from = matcher.end();
        }
        letters(out, text.substring(from));
        return out.toString();
    }

    private static void letters(final StringBuilder out, final String text) {
        text.chars().forEach(c -> out.append(LETTERS.getOrDefault((char) c, String.valueOf((char) c))));
    }
}
