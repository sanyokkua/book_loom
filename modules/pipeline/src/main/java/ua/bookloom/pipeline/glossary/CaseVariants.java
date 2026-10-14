package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Folds the spellings of one name that differ only in letter case into one candidate before the junk rules read them:
 * {@link ua.bookloom.util.text.JunkRules} drops a name in capitals, so {@code KESSEL OPTIK} would be lost while
 * {@code Kessel Optik} is kept, and the person would see one name twice if both lived. The base is the spelling that is
 * not written in capitals, else the commoner one (the earlier on a tie); the others become its aliases and their
 * counts add, so every spelling shares one entry and one target.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseVariants {

    private static final int MIN_CAPITAL_LETTERS = 2;

    /** The candidates with every group of case variants folded into one; the others keep their order. */
    static Map<String, NameCandidate> fold(final Map<String, NameCandidate> tallies) {
        final Map<String, List<NameCandidate>> groups = new LinkedHashMap<>();
        tallies.values()
                .forEach(tally -> groups.computeIfAbsent(tally.term().toLowerCase(Locale.ROOT), _ -> new ArrayList<>())
                        .add(tally));
        final Map<String, NameCandidate> folded = new LinkedHashMap<>();
        for (final List<NameCandidate> group : groups.values()) {
            final NameCandidate merged = merge(group);
            folded.put(merged.term(), merged);
        }
        return folded;
    }

    private static NameCandidate merge(final List<NameCandidate> group) {
        if (group.size() == 1) {
            return group.getFirst();
        }
        final Comparator<NameCandidate> commoner =
                Comparator.comparingInt(NameCandidate::count).reversed();
        final NameCandidate base = group.stream()
                .filter(tally -> !isInCapitals(tally.term()))
                .min(commoner)
                .or(() -> group.stream().min(commoner))
                .orElseThrow();
        final List<String> aliases = new ArrayList<>();
        group.stream().filter(tally -> !tally.equals(base)).forEach(tally -> aliases.add(tally.term()));
        final int count = group.stream().mapToInt(NameCandidate::count).sum();
        log.debug("Name candidate folds {} case variants into one, count {}", aliases.size(), count);
        log.trace("Name candidate {} has the case variants {}", base.term(), aliases);
        return new NameCandidate(base.term(), count, base.firstSentence(), aliases);
    }

    private static boolean isInCapitals(final String term) {
        return term.chars().filter(Character::isLetter).count() >= MIN_CAPITAL_LETTERS
                && term.equals(term.toUpperCase(Locale.ROOT));
    }
}
