package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.index.Occurrences;

/**
 * Folds the plural of a capitalised word that the book writes the way a common plural is written (15i.N5 junk): a
 * single-word candidate that is another capitalised word plus a plural ending ({@code Velarians}, {@code Kessels}) and
 * stands after a determiner in at least half its uses ({@link NounUses#isWrittenAsPlural}) is that word's plural, not a
 * name of its own — the alias of the candidate that word is, or nothing when the word is only ever part of a longer name
 * ({@code Kessel} of {@code Kessel Optik}). A plural with no determiner before it ({@code Andrews}) or whose singular
 * is neither a candidate nor a word of one ({@code the Larkfeathers}, a gang) is left to
 * {@link NameAliases}' count rule.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PluralNouns {

    private static final int MIN_BASE_LENGTH = 3;
    private static final Pattern SPACES = Pattern.compile(" ");

    /**
     * The candidates with each such plural folded or left out; the others keep their order.
     *
     * @param tallies the candidates by term; never null
     * @param nouns how the scanned text writes each capitalised word; never null
     * @param suffixes the language's plural endings; empty folds nothing
     * @return the candidates; never null
     */
    static Map<String, NameCandidate> fold(
            final Map<String, NameCandidate> tallies, final NounUses nouns, final List<String> suffixes) {
        final Map<String, NameCandidate> result = new LinkedHashMap<>(tallies);
        int folded = 0;
        int dropped = 0;
        for (final NameCandidate tally : tallies.values()) {
            final Optional<String> base = singularOf(tally.term(), tallies, nouns, suffixes);
            if (base.isEmpty()) {
                continue;
            }
            result.remove(tally.term());
            final Optional<NameCandidate> holder = holderOf(base.get(), result);
            holder.ifPresent(found -> result.put(found.term(), withAlias(found, tally)));
            log.trace("Plural {} of {} {}", tally.term(), base.get(), holder.isPresent() ? "folded" : "dropped");
            folded += holder.isPresent() ? 1 : 0;
            dropped += holder.isPresent() ? 0 : 1;
        }
        log.debug("Plural nouns: {} folded into their singular, {} dropped as the plural of a part", folded, dropped);
        return result;
    }

    // The singular must itself be a candidate, or a word of a longer one: a word capitalised only somewhere else
    // (a word capitalised once in some other phrase) says nothing.
    private static Optional<String> singularOf(
            final String term,
            final Map<String, NameCandidate> tallies,
            final NounUses nouns,
            final List<String> suffixes) {
        if (term.indexOf(' ') >= 0 || !nouns.isWrittenAsPlural(term)) {
            return Optional.empty();
        }
        return suffixes.stream()
                .filter(suffix -> term.length() - suffix.length() >= MIN_BASE_LENGTH && term.endsWith(suffix))
                .map(suffix -> term.substring(0, term.length() - suffix.length()))
                .filter(base -> isNamed(Occurrences.keyOf(base), tallies))
                .findFirst();
    }

    private static boolean isNamed(final String key, final Map<String, NameCandidate> tallies) {
        return tallies.keySet().stream()
                .anyMatch(term -> Arrays.stream(SPACES.split(term))
                        .map(Occurrences::keyOf)
                        .anyMatch(key::equals));
    }

    private static Optional<NameCandidate> holderOf(final String base, final Map<String, NameCandidate> tallies) {
        final String key = Occurrences.keyOf(base);
        return tallies.values().stream()
                .filter(candidate -> Occurrences.keyOf(candidate.term()).equals(key))
                .findFirst();
    }

    private static NameCandidate withAlias(final NameCandidate base, final NameCandidate plural) {
        final List<String> aliases = new ArrayList<>(base.aliases());
        aliases.add(plural.term());
        aliases.addAll(plural.aliases());
        return new NameCandidate(
                base.term(), base.count() + plural.count(), base.firstSentence(), aliases, base.readingOnly());
    }
}
