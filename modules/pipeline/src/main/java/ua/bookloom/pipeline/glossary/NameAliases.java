package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.PluralSuffixes;

/**
 * Folds a name candidate that is only another spelling of a commoner one into it: the plural of a name ({@code Chromes}
 * beside {@code Chrome}, the endings from the language file's {@code pluralSuffixes}) that the book writes at most half
 * as often, or a name one edit away that the book writes at most a fifth as often (a typo). The variant is not proposed on its own; the commoner name carries it
 * as an alias, so both spellings share one glossary entry and one target.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameAliases {

    /** A variant one edit away must occur at most this share of the commoner name's count. */
    static final double TYPO_COUNT_SHARE = 0.2;

    /**
     * A plural must occur at most this share of its base's count: the base at least twice as common. A name that only
     * looks like a plural ({@code Andrews} beside {@code Andrew}, {@code Hans} beside {@code Han}) is about as common
     * as the shorter one, or commoner, and stays a name of its own.
     */
    static final double PLURAL_COUNT_SHARE = 0.5;

    private static final int MIN_BASE_LENGTH = 3;
    private static final int MIN_TYPO_BASE_LENGTH = 5;

    /** The commonest of {@code others} that {@code term} is a plural or a typo of, or empty when it is neither. */
    static Optional<String> baseOf(
            final String term, final int count, final Map<String, Integer> others, final List<String> suffixes) {
        final String key = Occurrences.keyOf(term);
        return others.entrySet().stream()
                .filter(other -> !other.getKey().equals(term))
                .filter(other -> isVariant(key, count, other.getKey(), other.getValue(), suffixes))
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey);
    }

    /** The candidates with every variant folded into its base; the others keep their order. */
    static Map<String, NameCandidate> fold(final Map<String, NameCandidate> tallies, final List<String> suffixes) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        tallies.forEach((term, tally) -> counts.put(term, tally.count()));
        final Map<String, List<NameCandidate>> folded = new LinkedHashMap<>();
        for (final NameCandidate tally : tallies.values()) {
            final Optional<String> base = baseOf(tally.term(), tally.count(), counts, suffixes)
                    .filter(found -> baseOf(found, counts.getOrDefault(found, 0), counts, suffixes)
                            .isEmpty());
            base.ifPresent(found -> {
                log.debug("Name candidate folded into {} as an alias", found);
                log.trace("Name candidate {} x{} is an alias of {}", tally.term(), tally.count(), found);
                folded.computeIfAbsent(found, key -> new ArrayList<>()).add(tally);
            });
        }
        final Map<String, NameCandidate> result = new LinkedHashMap<>();
        tallies.forEach((term, tally) -> {
            if (!isFolded(term, folded)) {
                result.put(term, withAliases(tally, folded.getOrDefault(term, List.of())));
            }
        });
        return result;
    }

    private static boolean isFolded(final String term, final Map<String, List<NameCandidate>> folded) {
        return folded.values().stream()
                .anyMatch(list -> list.stream().anyMatch(v -> v.term().equals(term)));
    }

    private static NameCandidate withAliases(final NameCandidate base, final List<NameCandidate> variants) {
        if (variants.isEmpty()) {
            return base;
        }
        final int count =
                base.count() + variants.stream().mapToInt(NameCandidate::count).sum();
        return new NameCandidate(
                base.term(),
                count,
                base.firstSentence(),
                variants.stream().map(NameCandidate::term).toList());
    }

    private static boolean isVariant(
            final String key,
            final int count,
            final String otherTerm,
            final int otherCount,
            final List<String> suffixes) {
        final String other = Occurrences.keyOf(otherTerm);
        if (other.length() >= MIN_BASE_LENGTH
                && key.length() > other.length()
                && key.startsWith(other)
                && suffixes.contains(key.substring(other.length()))) {
            final boolean rarer = count <= otherCount * PLURAL_COUNT_SHARE;
            log.trace("Plural-shaped candidate {} x{} beside {} x{} rarer={}", key, count, other, otherCount, rarer);
            return rarer;
        }
        return other.length() >= MIN_TYPO_BASE_LENGTH
                && count <= otherCount * TYPO_COUNT_SHARE
                && isOneEdit(key, other);
    }

    static boolean isOneEdit(final String left, final String right) {
        if (Math.abs(left.length() - right.length()) > 1 || left.equals(right)) {
            return false;
        }
        final int common = commonPrefix(left, right);
        final String restLeft = left.substring(common);
        final String restRight = right.substring(common);
        if (restLeft.length() == restRight.length()) {
            return restLeft.substring(1).equals(restRight.substring(1));
        }
        return restLeft.length() > restRight.length()
                ? restLeft.substring(1).equals(restRight)
                : restRight.substring(1).equals(restLeft);
    }

    private static int commonPrefix(final String left, final String right) {
        int index = 0;
        while (index < left.length() && index < right.length() && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    static List<String> suffixesOf(final String languageTag) {
        return PluralSuffixes.of(languageTag);
    }
}
