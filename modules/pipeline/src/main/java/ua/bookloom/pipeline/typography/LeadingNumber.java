package ua.bookloom.pipeline.typography;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Puts back the number a heading opens with when the target lost it: a source starting with digits or a Roman numeral
 * and then {@code .}, {@code )} or {@code :} gives its number and mark back to a target that starts with a letter. A
 * target that starts with a number of its own is left alone, whatever the number, because a changed number is a
 * different case. A Markdown heading marker before the number is looked past and kept.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LeadingNumber {

    private static final String HEADING = "(?<marker>#{1,6}[ \\t]+)?";
    private static final String ROMAN = "M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})";
    private static final String NUMBER = "(?<number>\\d{1,3}|(?=[MDCLXVI])" + ROMAN + "(?![\\p{L}\\p{N}]))";
    private static final Pattern SOURCE = Pattern.compile("^" + HEADING + NUMBER + "(?<mark>[.):])[ \\t\\u00A0]+");
    private static final Pattern TARGET = Pattern.compile("^" + HEADING + NUMBER + "[.):]");
    private static final Pattern TARGET_LETTER = Pattern.compile("^" + HEADING + "\\p{L}");

    static Edit apply(final String source, final String text) {
        final Matcher lead = SOURCE.matcher(source);
        if (!lead.find() || TARGET.matcher(text).find()) {
            return new Edit(text, 0);
        }
        final Matcher target = TARGET_LETTER.matcher(text);
        if (!target.find()) {
            return new Edit(text, 0);
        }
        final String marker = target.group("marker") == null ? "" : target.group("marker");
        final String number = lead.group("number") + lead.group("mark") + " ";
        return new Edit(marker + number + text.substring(marker.length()), 1);
    }
}
