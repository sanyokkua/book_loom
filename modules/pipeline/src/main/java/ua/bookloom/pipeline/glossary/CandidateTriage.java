package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;

/**
 * The code's last word on a list of name candidates before any model reads it (15i.N3). It measures every candidate
 * over the whole book through the index, whatever slice the caller scanned: a candidate that is mostly written in
 * headings, title lines, contents entries and the book's non-story units, and in fewer than {@link #KEEP_NARRATION}
 * places of the story's text, is a section name or a converter's credit and is dropped; a pair written joined is one
 * candidate ({@link JoinedNames}). Speech counts as the story: a name only ever spoken is still a name.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CandidateTriage {

    /** A candidate with this share of its mentions outside the story's text, or more, is dropped... */
    static final int OUTSIDE_SHARE_PERCENT = 70;

    /** ...unless the story's text writes it this often. */
    static final int KEEP_NARRATION = 3;

    private static final int PERCENT = 100;

    /**
     * Triages the candidates.
     *
     * @param index the book's index; never null
     * @param candidates the candidates in rank order; never null
     * @param minCount the fewest occurrences a candidate needs, which a joined pair must also reach
     * @return the candidates that remain, by count descending and otherwise in the order given; never null
     */
    static List<NameCandidate> apply(final BookIndex index, final List<NameCandidate> candidates, final int minCount) {
        log.debug("Name triage of {} candidates, minimum count {}", candidates.size(), minCount);
        if (candidates.isEmpty()) {
            return candidates;
        }
        final Map<String, List<Mention>> byName = mentionsOfEveryName(index, candidates);
        final Set<String> terms = candidates.stream().map(NameCandidate::term).collect(Collectors.toSet());
        final Map<String, JoinedNames.Pair> pairOfHalf = new HashMap<>();
        JoinedNames.find(index, terms, byName)
                .forEach(pair -> pair.halves().forEach(half -> pairOfHalf.put(half, pair)));
        final List<NameCandidate> remaining = new ArrayList<>();
        final Set<String> placed = new HashSet<>();
        for (final NameCandidate candidate : candidates) {
            final JoinedNames.Pair pair = pairOfHalf.get(candidate.term());
            if (pair != null) {
                place(pair, minCount, remaining, placed, index);
            } else if (isNotOutsideTheStory(index, candidate, mentionsOf(candidate, byName))) {
                remaining.add(candidate);
            }
        }
        remaining.sort(Comparator.comparingInt(NameCandidate::count).reversed());
        log.debug("Name triage kept {} of {} candidates", remaining.size(), candidates.size());
        return remaining;
    }

    private static void place(
            final JoinedNames.Pair pair,
            final int minCount,
            final List<NameCandidate> remaining,
            final Set<String> placed,
            final BookIndex index) {
        if (!placed.add(pair.candidate().term())) {
            return;
        }
        if (pair.candidate().count() >= minCount && isNotOutsideTheStory(index, pair.candidate(), pair.mentions())) {
            remaining.add(pair.candidate());
        } else {
            log.debug(
                    "Joined name {} not proposed: {} uses",
                    pair.candidate().term(),
                    pair.candidate().count());
        }
    }

    private static Map<String, List<Mention>> mentionsOfEveryName(
            final BookIndex index, final List<NameCandidate> candidates) {
        final Set<String> names = new LinkedHashSet<>();
        candidates.forEach(candidate -> {
            names.add(candidate.term());
            names.addAll(candidate.aliases());
        });
        final Map<String, List<Mention>> byName = new LinkedHashMap<>();
        index.mentions(names, Case.SENSITIVE)
                .forEach(mention -> byName.computeIfAbsent(mention.name(), _ -> new ArrayList<>())
                        .add(mention));
        return byName;
    }

    private static List<Mention> mentionsOf(final NameCandidate candidate, final Map<String, List<Mention>> byName) {
        final List<Mention> all = new ArrayList<>(byName.getOrDefault(candidate.term(), List.of()));
        candidate.aliases().forEach(alias -> all.addAll(byName.getOrDefault(alias, List.of())));
        return all;
    }

    private static boolean isNotOutsideTheStory(
            final BookIndex index, final NameCandidate candidate, final List<Mention> mentions) {
        final long inStory = mentions.stream().filter(index::isStoryText).count();
        final long total = mentions.size();
        final long outside = total - inStory;
        final boolean drop =
                total > 0 && outside * PERCENT >= total * OUTSIDE_SHARE_PERCENT && inStory < KEEP_NARRATION;
        if (drop) {
            log.debug(
                    "Name candidate dropped: {} of {} mentions are outside the story's text, {} inside",
                    outside,
                    total,
                    inStory);
            log.trace("Name candidate {} dropped as a heading or credit", candidate.term());
        }
        return !drop;
    }
}
