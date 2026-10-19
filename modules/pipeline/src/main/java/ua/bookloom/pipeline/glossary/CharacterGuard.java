package ua.bookloom.pipeline.glossary;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * What is never a character (15j.J10): an honorific or a title ({@code Mrs}, {@code Duchess}, {@code Prime Minister})
 * is a title entry with no gender, and a common noun of the source language is no one. A common noun is a stop word
 * or a word that never stands alone as a name (the bundled lists), or a word the book also writes in lower case often
 * enough ({@link FrequencyScan#LOWER_SHARE_LIMIT}) with no pronouns after it and no capital in the middle of sentences
 * to show it is somebody ({@code Rose} followed by "she" stays a character). The readings of a small model call such words persons, and a glossary entry
 * that is a person waits on a gender for every segment that mentions it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CharacterGuard {

    private static final Pattern WORD_BREAK = Pattern.compile("[\\s-]+");
    private static final int MIN_PRONOUNS = 2;
    private static final int MIN_CAPITALISED_USES = 3;
    private static final String OFFICE_TITLES = "officeTitles";

    /**
     * Where a term the readings call a person really belongs.
     *
     * @param facts the readings and what the code knows of the term; never null
     * @return {@link TermType#TITLE} for an honorific or a title, {@link TermType#OTHER} for a common noun, or empty
     *     when the term may be a character
     */
    static Optional<TermType> demotion(final Facts facts) {
        final String term = facts.entry().term();
        final String language = facts.analysisLanguage();
        if (isTitle(term, language)) {
            log.debug(
                    "Character guard: entry {} is an honorific or a title",
                    facts.entry().id());
            return Optional.of(TermType.TITLE);
        }
        if (isCommonNoun(term, facts, language)) {
            log.debug(
                    "Character guard: entry {} is a common noun, not a character",
                    facts.entry().id());
            return Optional.of(TermType.OTHER);
        }
        return Optional.empty();
    }

    // A title is a listed honorific or title word on its own, or a run of capitalised words that ends in an office
    // (Prime Minister): the language file's officeTitles.
    private static boolean isTitle(final String term, @Nullable final String language) {
        final String[] words = WORD_BREAK.split(term.strip().replaceAll("\\.$", ""));
        final String last = Occurrences.keyOf(words[words.length - 1]);
        if (StopWords.titles(language).contains(last)) {
            return true;
        }
        return words.length > 1
                && officeTitles(language).contains(last)
                && Arrays.stream(words).allMatch(CharacterGuard::isCapitalised);
    }

    private static boolean isCapitalised(final String word) {
        return !word.isEmpty() && Character.isUpperCase(word.codePointAt(0));
    }

    private static Set<String> officeTitles(@Nullable final String language) {
        return language == null ? Set.of() : Set.copyOf(LanguageRules.bundled().wordsOf(language, OFFICE_TITLES));
    }

    private static boolean isCommonNoun(final String term, final Facts facts, @Nullable final String language) {
        final String key = Occurrences.keyOf(term.strip().toLowerCase(Locale.ROOT));
        if (StopWords.of(language).contains(key)
                || StopWords.neverAlone(language).contains(key)) {
            return true;
        }
        return facts.lowerShare() >= FrequencyScan.LOWER_SHARE_LIMIT && !hasNameEvidence(facts);
    }

    // A real name that is also a word (Rose) shows it by a clear or a few pronouns after it, or by being written with a
    // capital in the middle of sentences; a common noun only opens sentences with a capital.
    private static boolean hasNameEvidence(final Facts facts) {
        final PronounGender.Evidence pronouns = facts.evidence().pronouns();
        final boolean tallied =
                TermReviewReplies.favouredBy(pronouns) != null || pronouns.female() + pronouns.male() >= MIN_PRONOUNS;
        final boolean capitalised = facts.capitalisedMidSentence() >= MIN_CAPITALISED_USES;
        log.debug("Character guard: pronoun evidence {}, capitalised mid-sentence {}", tallied, capitalised);
        return tallied || capitalised;
    }
}
