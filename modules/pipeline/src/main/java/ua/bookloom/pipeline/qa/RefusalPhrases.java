package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.lang.LanguageTags;

/**
 * The refusal-phrase catalogue {@link RefusalGate} matches a candidate target against, keyed by language tag
 * (design D8) — an apology or a comment about the task must never reach the book, so the anchored prefixes below are
 * a fixed list, not a learned one.
 */
// Each constant's List.of(...) is genuinely immutable at runtime; Error Prone's ImmutableEnumChecker only inspects
// the declared field type, which it cannot prove immutable for the general java.util.List interface.
@SuppressWarnings("ImmutableEnumChecker")
enum RefusalPhrases {

    /** English refusal openers a small local model tends to produce instead of translating. */
    EN(
            "en",
            List.of(
                    "I'm sorry, but I can't translate",
                    "I am sorry, but I cannot translate",
                    "I cannot translate",
                    "I can't translate",
                    "I am unable to translate",
                    "I'm unable to translate",
                    "As an AI",
                    "As a language model",
                    "Here is the translation",
                    "Here's the translation",
                    "Translation:",
                    "Sure, here is the translation")),

    /** Ukrainian refusal openers, mirroring {@link #EN}'s list. */
    UK(
            "uk",
            List.of(
                    "Вибачте, я не можу перекласти",
                    "Я не можу перекласти",
                    "Не можу перекласти",
                    "Як мовна модель",
                    "Як ШІ",
                    "Ось переклад",
                    "Переклад:"));

    private final String languageTag;
    private final List<String> phrases;

    RefusalPhrases(final String languageTag, final List<String> phrases) {
        this.languageTag = languageTag;
        this.phrases = phrases;
    }

    /**
     * This language's anchored refusal phrases, each tested at the start of a trimmed display text.
     *
     * @return the phrase list; never empty
     */
    List<String> phrases() {
        return phrases;
    }

    /**
     * Looks a language's refusal phrases up from a raw, possibly un-normalized tag.
     *
     * @param tag the raw tag, possibly {@code null}, blank, or naming no catalogued language
     * @return the matching constant's phrases, or empty when {@code tag} normalizes to no constant here
     */
    static List<String> phrasesFor(@Nullable final String tag) {
        return LanguageTags.normalize(tag)
                .flatMap(RefusalPhrases::byTag)
                .map(RefusalPhrases::phrases)
                .orElseGet(List::of);
    }

    private static Optional<RefusalPhrases> byTag(final String normalizedTag) {
        return Stream.of(values())
                .filter(candidate -> candidate.languageTag.equals(normalizedTag))
                .findFirst();
    }
}
