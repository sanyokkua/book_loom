package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.prompt.NounPhrases;
import ua.bookloom.pipeline.prompt.Pronouns;

/**
 * How a book writes each capitalised word away from a sentence start and outside a longer run (15i.N5 junk): after a
 * determiner or not, between an indefinite article and a noun, and which subject pronoun follows it. A person's name is
 * rarely written after a determiner, so a word written after one in nearly every use is a common noun or an adjective
 * written with a capital when the book also shows what kind: an adjective ({@code a Velarian porter}), or a group the
 * pronoun {@code they} follows. A spell or a thing ({@code a Seal on the door}) is followed by a function word, not a
 * noun, and a gang's plural name with no {@code they} after it stays a name. The words come from the language file
 * ({@link NounPhrases}, {@link Pronouns}); a language that lists none gets none of these rules.
 */
@Slf4j
final class NounUses {

    /** A word written after a determiner at least this share of its uses is written as a common noun. */
    static final double DETERMINER_SHARE = 0.9;

    /** A plural-shaped word written after a determiner at least this share of its uses is the plural of a word. */
    static final double PLURAL_DETERMINER_SHARE = 0.5;

    /** The fewest uses a plural needs before it is judged. */
    static final int MIN_PLURAL_USES = 2;

    /** The fewest uses an adjective needs before it is judged. */
    static final int MIN_ADJECTIVE_USES = 2;

    /** The fewest uses a group needs before it is judged. */
    static final int MIN_GROUP_USES = 3;

    /** A group needs this many uses that a plural subject pronoun follows, more than any gendered one. */
    static final int MIN_PLURAL_FOLLOWS = 2;

    private static final Pattern SPACES = Pattern.compile("\\p{Zs}+");

    /** One word's uses: how many, and how many show each sign. */
    private static final class Tally {
        private int uses;
        private int afterDeterminer;
        private int attributive;
        private int plural;
        private int gendered;
    }

    private record Words(
            Set<String> determiners,
            Set<String> indefinite,
            Set<String> stop,
            Set<String> plural,
            Set<String> gendered) {

        boolean isEmpty() {
            return determiners.isEmpty();
        }
    }

    private final Map<String, Tally> tallies;

    private NounUses(final Map<String, Tally> tallies) {
        this.tallies = tallies;
    }

    /**
     * Reads every capitalised word of the given segments.
     *
     * @param reads the segments' scan reads; never null
     * @param language the source language tag; null or a language with no determiners reads nothing
     * @return the uses; never null
     */
    static NounUses of(final List<Occurrences.Read> reads, @Nullable final String language) {
        final Words words = wordsOf(language);
        final Map<String, Tally> tallies = new HashMap<>();
        if (!words.isEmpty()) {
            reads.forEach(read -> count(read, words, tallies));
        }
        log.debug(
                "Noun uses over {} segments, language {}: {} capitalised words",
                reads.size(),
                language,
                tallies.size());
        return new NounUses(tallies);
    }

    /**
     * Leaves out every single-word candidate the uses show to be a common noun or an adjective. A word of a longer
     * candidate is that name's short form ({@code the Institute} of a longer institute's name, written {@code an
     * Institute notebook}) and stays.
     *
     * @param tallies the candidates by term, changed in place; never null
     */
    void dropFrom(final Map<String, NameCandidate> tallies) {
        final List<String> longer =
                tallies.keySet().stream().filter(term -> term.indexOf(' ') >= 0).toList();
        final int before = tallies.size();
        tallies.keySet().removeIf(term -> {
            final String key = Occurrences.keyOf(term);
            if (longer.stream().anyMatch(run -> NameTally.containsWord(run, key))) {
                return false;
            }
            final Optional<String> reason = rejection(term);
            reason.ifPresent(why -> {
                log.debug("Name candidate dropped as a common noun: {}", why);
                log.trace("Name candidate {} dropped: {}", term, why);
            });
            return reason.isPresent();
        });
        log.debug("Noun uses dropped {} of {} candidates", before - tallies.size(), before);
    }

    /**
     * Why a single-word candidate is a common noun or an adjective and not a name.
     *
     * @param term the candidate as written; a run of two words or more is never judged
     * @return the reason, or empty when the uses do not show it
     */
    Optional<String> rejection(final String term) {
        final Tally tally = tallies.get(Occurrences.keyOf(term));
        if (term.indexOf(' ') >= 0 || tally == null) {
            return Optional.empty();
        }
        final boolean asNoun = tally.afterDeterminer >= tally.uses * DETERMINER_SHARE;
        log.trace(
                "Noun uses of {}: {} uses, {} after a determiner, {} before a noun, {} plural and {} gendered pronouns",
                term,
                tally.uses,
                tally.afterDeterminer,
                tally.attributive,
                tally.plural,
                tally.gendered);
        if (asNoun && tally.uses >= MIN_ADJECTIVE_USES && tally.attributive > 0) {
            return Optional.of("an adjective: written after a determiner and before a noun");
        }
        if (asNoun
                && tally.uses >= MIN_GROUP_USES
                && tally.plural >= MIN_PLURAL_FOLLOWS
                && tally.plural > tally.gendered) {
            return Optional.of("a group: written after a determiner, and \"they\" follows it");
        }
        return Optional.empty();
    }

    /**
     * Whether a plural-shaped word is written the way the plural of a common word is: after a determiner in at least
     * {@link #PLURAL_DETERMINER_SHARE} of its uses ({@code the Velarians}, {@code every Kessels}).
     *
     * @param term the word as written
     * @return {@code true} if it is, {@code false} otherwise or when it is written mid-sentence fewer than
     *     {@link #MIN_PLURAL_USES} times
     */
    boolean isWrittenAsPlural(final String term) {
        final Tally tally = tallies.get(Occurrences.keyOf(term));
        return tally != null
                && tally.uses >= MIN_PLURAL_USES
                && tally.afterDeterminer >= tally.uses * PLURAL_DETERMINER_SHARE;
    }

    private static Words wordsOf(@Nullable final String language) {
        if (language == null) {
            return new Words(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
        final Set<String> gendered = new HashSet<>(Pronouns.female(language));
        gendered.addAll(Pronouns.male(language));
        return new Words(
                NounPhrases.determiners(language),
                NounPhrases.indefinite(language),
                StopWords.of(language),
                Pronouns.plural(language),
                Set.copyOf(gendered));
    }

    private static void count(final Occurrences.Read read, final Words words, final Map<String, Tally> tallies) {
        final List<Occurrences.Word> all = read.words();
        for (int index = 0; index < all.size(); index++) {
            final Occurrences.Word word = all.get(index);
            if (word.capitalised() && !word.initial() && !isInsideARun(read, index)) {
                final Tally tally = tallies.computeIfAbsent(word.key(), _ -> new Tally());
                tally.uses++;
                final Occurrences.Word before = neighbour(all, index, -1);
                final boolean afterDeterminer =
                        before != null && words.determiners().contains(before.key());
                tally.afterDeterminer += afterDeterminer ? 1 : 0;
                tally.attributive += isBeforeANoun(read, index, before, words) ? 1 : 0;
                follow(all, index, words, tally);
            }
        }
    }

    // A capitalised mid-sentence word right before or after, with only spaces between, makes the word part of a run;
    // a sentence-initial one before it ("A Velarian", "The Guildsmen") is the determiner, not a name.
    private static boolean isInsideARun(final Occurrences.Read read, final int index) {
        final List<Occurrences.Word> all = read.words();
        final Occurrences.Word word = all.get(index);
        final Occurrences.Word before = neighbour(all, index, -1);
        final Occurrences.Word after = neighbour(all, index, 1);
        final boolean nameBefore =
                before != null && before.capitalised() && !before.initial() && isSpaced(read, before, word);
        final boolean nameAfter = after != null && after.capitalised() && isSpaced(read, word, after);
        return nameBefore || nameAfter;
    }

    private static boolean isBeforeANoun(
            final Occurrences.Read read, final int index, final Occurrences.@Nullable Word before, final Words words) {
        final Occurrences.Word after = neighbour(read.words(), index, 1);
        return before != null
                && words.indefinite().contains(before.key())
                && after != null
                && !after.capitalised()
                && !words.stop().contains(after.key())
                && isSpaced(read, read.words().get(index), after);
    }

    // The first subject pronoun in the rest of the sentence and the next, unless another name comes first.
    private static void follow(
            final List<Occurrences.Word> all, final int index, final Words words, final Tally tally) {
        final int sentence = all.get(index).sentence();
        for (int next = index + 1; next < all.size() && all.get(next).sentence() <= sentence + 1; next++) {
            final Occurrences.Word word = all.get(next);
            if (words.plural().contains(word.key())) {
                tally.plural++;
                return;
            }
            if (words.gendered().contains(word.key())) {
                tally.gendered++;
                return;
            }
            if (word.capitalised() && !word.initial()) {
                return;
            }
        }
    }

    private static Occurrences.@Nullable Word neighbour(
            final List<Occurrences.Word> all, final int index, final int step) {
        final int other = index + step;
        return other < 0
                        || other >= all.size()
                        || all.get(other).sentence() != all.get(index).sentence()
                ? null
                : all.get(other);
    }

    private static boolean isSpaced(
            final Occurrences.Read read, final Occurrences.Word left, final Occurrences.Word right) {
        return SPACES.matcher(read.text().substring(left.end(), right.start())).matches();
    }
}
