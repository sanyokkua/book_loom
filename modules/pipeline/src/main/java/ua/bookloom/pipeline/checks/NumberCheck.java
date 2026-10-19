package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether every number the source writes in digits is still in the target (408 that became 48). Numbers are compared
 * as values, so a thousands separator, a decimal comma and a space between digit groups do not matter. A number of one
 * digit is left alone, since it is often spelled out; a longer one is reported when it is missing and another number
 * stands in the target (a change), or when it is missing and has three digits or more, or a time or a decimal part,
 * which are rarely written in words. A rule that can mistake a spelled-out number must never block, so it is soft.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NumberCheck {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,:]\\d+)*");
    private static final Pattern GROUP_SPACE = Pattern.compile("(?<=\\d)[\\s  ](?=\\d{3}(?!\\d))");
    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");
    private static final int LONG_NUMBER_DIGITS = 3;

    static List<CheckFinding> find(final String source, final String target) {
        final Map<String, Integer> wanted = numbers(source);
        if (wanted.isEmpty()) {
            return List.of();
        }
        final Map<String, Integer> written = numbers(target);
        final List<String> missing = wanted.keySet().stream()
                .filter(number -> !written.containsKey(number))
                .toList();
        final List<String> extra = written.keySet().stream()
                .filter(number -> !wanted.containsKey(number))
                .toList();
        final List<CheckFinding> found = new ArrayList<>();
        for (final String number : missing) {
            if (!extra.isEmpty() || isHardToSpell(number)) {
                found.add(finding(number, extra, target));
            }
        }
        if (!found.isEmpty()) {
            log.debug("Number check: {} of {} source numbers changed or lost", found.size(), wanted.size());
        }
        return List.copyOf(found);
    }

    // Number value → its first start in the text (as written, so the span points at it).
    private static Map<String, Integer> numbers(final String text) {
        final String joined = GROUP_SPACE.matcher(text).replaceAll("");
        final Map<String, Integer> numbers = new LinkedHashMap<>();
        final Matcher found = NUMBER.matcher(joined);
        while (found.find()) {
            final String value = valueOf(found.group());
            if (value.length() > 1) {
                numbers.putIfAbsent(value, found.start());
            }
        }
        return numbers;
    }

    private static String valueOf(final String token) {
        if (THOUSANDS.matcher(token).matches()) {
            return token.replaceAll("[.,]", "");
        }
        return token.replace(',', '.');
    }

    private static boolean isHardToSpell(final String number) {
        return number.length() >= LONG_NUMBER_DIGITS || number.indexOf(':') >= 0 || number.indexOf('.') >= 0;
    }

    private static CheckFinding finding(final String number, final List<String> extra, final String target) {
        final String shown = extra.isEmpty() ? "" : extra.getFirst();
        final int at = shown.isEmpty() ? -1 : target.indexOf(shown);
        final TextSpan span = at < 0 ? new TextSpan(0, 0, "") : new TextSpan(at, at + shown.length(), shown);
        final String explanation = shown.isEmpty()
                ? "The source has the number " + number + " and the translation does not: write " + number
                        + " as the source does."
                : "The source has the number " + number + " and the translation has " + shown + " instead: write "
                        + number + " as the source does.";
        return new CheckFinding(FindingKind.NUMBER_CHANGED, span, explanation, false);
    }
}
