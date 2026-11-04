package ua.bookloom.pipeline.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/**
 * A repeated source paragraph translated more than one way (a refrain, a sign, a repeated line): the audit doubts each
 * paragraph that holds a target other than the most used one. Sources and targets are compared lower-case, without
 * punctuation and with white space collapsed, and a source of fewer than six words is not compared.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SourceVariants {

    static final String NAME = "source-variants";
    private static final int MIN_WORDS = 6;
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{P}\\p{S}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Finds the minority targets of repeated sources.
     *
     * @param sources the non-null display text of each audited segment's source, by segment id, in document order
     * @param targets the non-null display text of each audited segment's target, by segment id
     * @return the findings by the segment that holds a minority target; never null, empty when every repeated source
     *     has one target
     */
    static Map<String, List<QaFinding>> find(final Map<String, String> sources, final Map<String, String> targets) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(targets, "targets");
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        final Map<String, String> written = new LinkedHashMap<>();
        sources.forEach((segmentId, source) -> {
            final String key = normalised(source);
            final String target = targets.get(segmentId);
            if (target != null && wordCount(key) >= MIN_WORDS) {
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(segmentId);
                written.put(segmentId, normalised(target));
            }
        });
        final Map<String, List<QaFinding>> found = new LinkedHashMap<>();
        groups.values().stream().filter(ids -> ids.size() > 1).forEach(ids -> addMinority(ids, written, found));
        log.debug("Source variants: {} paragraph(s) hold the less used target of a repeated source", found.size());
        return found;
    }

    private static void addMinority(
            final List<String> ids, final Map<String, String> written, final Map<String, List<QaFinding>> found) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        ids.forEach(id -> counts.merge(written.get(id), 1, Integer::sum));
        if (counts.size() < 2) {
            return;
        }
        final String usual = usualOf(counts);
        for (final String id : ids) {
            if (!usual.equals(written.get(id))) {
                log.trace("Source variants: segment {} holds a target other than the usual one", id);
                found.computeIfAbsent(id, ignored -> new ArrayList<>()).add(finding(ids.size()));
            }
        }
    }

    // The most used target; a tie goes to the one seen first.
    private static String usualOf(final Map<String, Integer> counts) {
        String usual = "";
        int most = 0;
        for (final Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() > most) {
                usual = count.getKey();
                most = count.getValue();
            }
        }
        return usual;
    }

    private static QaFinding finding(final int occurrences) {
        return new QaFinding(
                "meaning",
                Severity.LOW,
                "This paragraph's source occurs " + occurrences + " times and is translated differently elsewhere:"
                        + " keep one translation.",
                NAME);
    }

    private static int wordCount(final String normalised) {
        return normalised.isEmpty() ? 0 : normalised.split(" ").length;
    }

    private static String normalised(final String text) {
        final String lower = text.toLowerCase(Locale.ROOT);
        return SPACES.matcher(PUNCTUATION.matcher(lower).replaceAll(" "))
                .replaceAll(" ")
                .strip();
    }
}
