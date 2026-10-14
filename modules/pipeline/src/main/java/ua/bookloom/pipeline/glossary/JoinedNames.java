package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Mention;

/**
 * Finds the names a book writes as a pair, {@code Orrin & Vale} or {@code Orrin and Vale}: when both halves are
 * written joined in at least {@link #JOINED_SHARE_PERCENT} percent of their uses, the pair is one name and neither half
 * is a name of its own. Below that the halves stay what they were (two characters who often walk in together).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JoinedNames {

    /** Both halves must be written joined at least this share of their uses. */
    static final int JOINED_SHARE_PERCENT = 80;

    private static final int PERCENT = 100;
    private static final Pattern CONNECTOR = Pattern.compile("\\p{Zs}+(&|and)\\p{Zs}+");

    /**
     * One pair accepted as a name.
     *
     * @param candidate the pair as a candidate: its term, the joined uses as its count, the first joined sentence
     * @param halves the terms of the two halves
     * @param mentions where the pair is written, each from the first half's start to the second half's end
     */
    record Pair(NameCandidate candidate, Set<String> halves, List<Mention> mentions) {}

    /** One place two names are written joined. */
    private record Join(String first, String second, String connector, Mention start, Mention end) {

        List<String> halves() {
            return List.of(first, second);
        }

        String written() {
            return first + " " + connector + " " + second;
        }

        Mention mention(final String term) {
            return new Mention(term, start.ordinal(), start.start(), end.end(), start.sentence(), start.narration());
        }
    }

    /**
     * Finds the pairs among the candidates.
     *
     * @param index the book's index, to read the text between two mentions and a sentence
     * @param terms the candidates' terms
     * @param byTerm the mentions of each term, case-sensitive, over the whole book
     * @return the accepted pairs, the commonest first, no half in two pairs; never null
     */
    static List<Pair> find(final BookIndex index, final Set<String> terms, final Map<String, List<Mention>> byTerm) {
        final Map<List<String>, List<Join>> joins = new LinkedHashMap<>();
        joinsIn(index, terms, byTerm)
                .forEach(join -> joins.computeIfAbsent(join.halves(), _ -> new ArrayList<>())
                        .add(join));
        final List<Pair> pairs = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        joins.values().stream()
                .sorted(Comparator.comparingInt((List<Join> list) -> list.size())
                        .reversed())
                .filter(list -> isOneName(list, byTerm))
                .forEach(list -> accept(index, list, used, pairs));
        log.debug("Joined names: {} pairs among {} candidates", pairs.size(), terms.size());
        return pairs;
    }

    private static List<Join> joinsIn(
            final BookIndex index, final Set<String> terms, final Map<String, List<Mention>> byTerm) {
        final Map<Integer, Map<Integer, List<Mention>>> startingAt = new HashMap<>();
        terms.forEach(term -> byTerm.getOrDefault(term, List.of())
                .forEach(mention -> startingAt
                        .computeIfAbsent(mention.ordinal(), _ -> new HashMap<>())
                        .computeIfAbsent(mention.start(), _ -> new ArrayList<>())
                        .add(mention)));
        final List<Join> joins = new ArrayList<>();
        for (final String term : terms) {
            for (final Mention first : byTerm.getOrDefault(term, List.of())) {
                joinAfter(index, first, startingAt.getOrDefault(first.ordinal(), Map.of()))
                        .ifPresent(joins::add);
            }
        }
        return joins;
    }

    private static Optional<Join> joinAfter(
            final BookIndex index, final Mention first, final Map<Integer, List<Mention>> startingAt) {
        final String text = index.visibleTexts(List.of(first.ordinal())).getFirst();
        final Matcher connector = CONNECTOR.matcher(text).region(first.end(), text.length());
        if (!connector.lookingAt()) {
            return Optional.empty();
        }
        return startingAt.getOrDefault(connector.end(), List.of()).stream()
                .filter(next -> !next.name().equals(first.name()))
                .max(Comparator.comparingInt(Mention::end))
                .map(next -> new Join(first.name(), next.name(), connector.group(1), first, next));
    }

    private static boolean isOneName(final List<Join> joins, final Map<String, List<Mention>> byTerm) {
        final Join any = joins.getFirst();
        final int count = joins.size();
        return count * PERCENT >= byTerm.getOrDefault(any.first(), List.of()).size() * JOINED_SHARE_PERCENT
                && count * PERCENT
                        >= byTerm.getOrDefault(any.second(), List.of()).size() * JOINED_SHARE_PERCENT;
    }

    private static void accept(
            final BookIndex index, final List<Join> joins, final Set<String> used, final List<Pair> pairs) {
        final Join first = joins.stream()
                .min(Comparator.comparingInt((Join join) -> join.start().ordinal())
                        .thenComparingInt(join -> join.start().start()))
                .orElseThrow();
        if (first.halves().stream().anyMatch(used::contains)) {
            log.debug("Joined name {} skipped: a half is already in another pair", first.written());
            return;
        }
        used.addAll(first.halves());
        final String term = first.written();
        final List<Mention> mentions =
                joins.stream().map(join -> join.mention(term)).toList();
        final String sentence = index.reads(List.of(first.start().ordinal()))
                .getFirst()
                .sentences()
                .get(first.start().sentence());
        final List<String> others = joins.stream()
                .map(Join::written)
                .distinct()
                .filter(form -> !form.equals(term))
                .toList();
        log.debug("Joined name is written joined {} times", joins.size());
        log.trace("Joined name {}: {}", term, sentence);
        pairs.add(new Pair(
                new NameCandidate(term, joins.size(), sentence, others), Set.copyOf(first.halves()), mentions));
    }
}
