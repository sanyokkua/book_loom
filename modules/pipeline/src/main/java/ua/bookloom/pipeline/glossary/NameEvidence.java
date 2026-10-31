package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.glossary.PronounReader.Pronoun;

/**
 * Reads one name's mentions for {@link PronounGender}: the pronouns after each mention ({@link PronounReader}) and the
 * past-tense verb after a mention written as the entry's term ({@link PastTenseReader}), a family's titled uses left
 * out.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameEvidence {

    static PronounGender.Evidence read(
            final String term,
            final String lemma,
            final List<String> texts,
            @Nullable final String languageTag,
            final BiPredicate<String, Mention> keep) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(lemma, "lemma");
        Objects.requireNonNull(texts, "texts");
        final PronounReader reader = PronounReader.of(term, languageTag);
        if (reader == null) {
            log.trace("Pronoun evidence skipped term={} language={}: no pronoun table", term, languageTag);
            return new PronounGender.Evidence(List.of(), List.of());
        }
        final List<Use> uses = new ArrayList<>();
        final PastTenseReader past = PastTenseReader.of(languageTag);
        final Predicate<String> namedElsewhere = namedInsideASentence(texts);
        texts.forEach(text -> reader.read(text, namedElsewhere).stream()
                .filter(use -> keep.test(text, use))
                .forEach(use -> uses.add(new Use(use, verbAfter(past, text, use, lemma)))));
        // A surname written after both a man's and a woman's form of address is a family's: those uses are the
        // titled people's, not the bare name's.
        final long titledGenders = uses.stream()
                .map(use -> use.mention().title())
                .filter(Objects::nonNull)
                .map(Pronoun::gender)
                .distinct()
                .count();
        final boolean family = titledGenders > 1;
        final PronounTally tally = new PronounTally();
        uses.stream()
                .filter(use -> use.mention().title() == null || !family)
                .forEach(use -> tally.add(use.mention(), use.verb()));
        log.trace("Pronoun evidence for {}: {} uses, family name {}", term, uses.size(), family);
        return tally.evidence();
    }

    private record Use(Mention mention, PastTenseReader.@Nullable Vote verb) {}

    // The verb after a mention written as the lemma itself, read in the text with its straight-quoted speech blanked.
    private static PastTenseReader.@Nullable Vote verbAfter(
            @Nullable final PastTenseReader past, final String text, final Mention use, final String lemma) {
        final int end = use.start() + lemma.length();
        final boolean isLemma = past != null
                && end <= text.length()
                && text.regionMatches(true, use.start(), lemma, 0, lemma.length())
                && (end == text.length() || !Character.isLetter(text.charAt(end)));
        return isLemma
                ? Objects.requireNonNull(past, "past")
                        .voteAfter(PronounReader.withoutStraightSpeech(text), use.start(), end)
                        .orElse(null)
                : null;
    }

    // Whether the book writes a word with a capital after a lower-case word or a comma, as only a name is written;
    // asked only of the few words that open a sentence right before a use, and remembered.
    private static Predicate<String> namedInsideASentence(final List<String> texts) {
        final String book = String.join("\n", texts);
        final Map<String, Boolean> asked = new HashMap<>();
        return word -> asked.computeIfAbsent(
                word,
                key -> Pattern.compile("[\\p{Ll},;:]\\p{Zs}+" + Pattern.quote(key) + "(?![\\p{L}\\p{M}])")
                        .matcher(book)
                        .find());
    }
}
