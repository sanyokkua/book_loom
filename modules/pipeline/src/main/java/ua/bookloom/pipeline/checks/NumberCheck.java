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
import ua.bookloom.pipeline.qa.CheckLevel;

/**
 * Whether the numbers the source writes in digits are still in the target, compared as values, so a thousands
 * separator, a decimal comma and a space between digit groups do not matter (design D12). A number the target starts
 * with that is another than the one the source starts with ({@code 4.} that became {@code 5.}) blocks. A number of two
 * digits or more that the target lacks is a repair; a number the target adds, when none is missing, is a note. A number
 * of one digit is not required, since it is often spelled out, and spelled-out numbers are never compared.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NumberCheck {

    private static final String AS_SOURCE_DOES = " as the source does.";

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{M}\\d])\\d+(?:[.,:]\\d+){0,32}");
    private static final Pattern GROUP_SPACE = Pattern.compile("(?<=\\d)[\\s\u00A0\u202F](?=\\d{3}(?!\\d))");
    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");
    private static final int MIN_REQUIRED_DIGITS = 2;
    private static final Pattern LEADING = Pattern.compile("^\\s*(\\d+|[IVXLCDM]+)[.):](?=\\s|$)");

    static List<CheckFinding> find(final String source, final String target) {
        final List<CheckFinding> found = new ArrayList<>();
        final String leadingSource = leading(source);
        final String leadingTarget = leading(target);
        final boolean replaced =
                !leadingSource.isEmpty() && !leadingTarget.isEmpty() && !leadingSource.equals(leadingTarget);
        if (replaced) {
            found.add(replacedLeading(leadingSource, leadingTarget, target));
        }
        final Map<String, Integer> wanted = numbers(source);
        final Map<String, Integer> written = numbers(target);
        if (replaced) {
            wanted.remove(leadingSource);
            written.remove(leadingTarget);
        }
        found.addAll(missingAndExtra(wanted, written, target));
        if (!found.isEmpty()) {
            log.debug("Number check: {} finding(s) over {} source numbers", found.size(), wanted.size());
        }
        return List.copyOf(found);
    }

    private static List<CheckFinding> missingAndExtra(
            final Map<String, Integer> wanted, final Map<String, Integer> written, final String target) {
        final List<String> missing = wanted.keySet().stream()
                .filter(number -> number.length() >= MIN_REQUIRED_DIGITS)
                .filter(number -> !written.containsKey(number))
                .toList();
        final List<String> extra = written.keySet().stream()
                .filter(number -> !wanted.containsKey(number))
                .toList();
        final List<CheckFinding> found = new ArrayList<>();
        for (final String number : missing) {
            found.add(lost(number, extra, target));
        }
        if (missing.isEmpty() && !extra.isEmpty()) {
            final String added = extra.getFirst();
            final int at = target.indexOf(added);
            log.debug("Number check: the target adds a number");
            found.add(new CheckFinding(
                    FindingKind.NUMBER_CHANGED,
                    new TextSpan(Math.max(at, 0), Math.max(at, 0) + added.length(), added),
                    "The translation has the number " + added + " and the source does not: remove it unless the"
                            + " source says so in words.",
                    CheckLevel.NOTE));
        }
        return found;
    }

    // The leading number's value (digits or Roman numeral before . ) or :), or "" when the text does not start with
    // one.
    private static String leading(final String text) {
        final Matcher found = LEADING.matcher(text);
        return found.find() ? valueOf(found.group(1)) : "";
    }

    private static CheckFinding replacedLeading(final String wanted, final String written, final String target) {
        final int at = Math.max(target.indexOf(written), 0);
        log.debug("Number check: the leading number is replaced");
        log.trace("Leading number {} became {}", wanted, written);
        return new CheckFinding(
                FindingKind.NUMBER_CHANGED,
                new TextSpan(at, at + written.length(), written),
                "The source starts with the number " + wanted + " and the translation starts with " + written
                        + " instead: start with " + wanted + AS_SOURCE_DOES,
                CheckLevel.BLOCK);
    }

    // Number value → its first start in the text (as written, so the span points at it).
    private static Map<String, Integer> numbers(final String text) {
        final String joined = GROUP_SPACE.matcher(text).replaceAll("");
        final Map<String, Integer> numbers = new LinkedHashMap<>();
        final Matcher found = NUMBER.matcher(joined);
        while (found.find()) {
            numbers.putIfAbsent(valueOf(found.group()), found.start());
        }
        return numbers;
    }

    private static String valueOf(final String token) {
        if (THOUSANDS.matcher(token).matches()) {
            return token.replaceAll("[.,]", "");
        }
        return token.replace(',', '.');
    }

    private static CheckFinding lost(final String number, final List<String> extra, final String target) {
        final String shown = extra.isEmpty() ? "" : extra.getFirst();
        final int at = shown.isEmpty() ? -1 : target.indexOf(shown);
        final TextSpan span = at < 0 ? new TextSpan(0, 0, "") : new TextSpan(at, at + shown.length(), shown);
        final String explanation = shown.isEmpty()
                ? "The source has the number " + number + " and the translation does not: write " + number
                        + AS_SOURCE_DOES
                : "The source has the number " + number + " and the translation has " + shown + " instead: write "
                        + number + AS_SOURCE_DOES;
        return new CheckFinding(FindingKind.NUMBER_CHANGED, span, explanation, CheckLevel.REPAIR);
    }
}
