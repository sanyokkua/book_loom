package ua.bookloom.pipeline.checks;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.glossary.StopWords;
import ua.bookloom.pipeline.prompt.Pronouns;

/**
 * What both name-spelling checks (the run-time one and the whole-book audit) agree on: two words are one name when they
 * are equal once doubled letters are collapsed and the language's voiced and voiceless consonant pairs are folded, and
 * a common word of the language (a pronoun, a stop word, any word the targets also write in lower case) is never a
 * spelling of a name, however well it folds into one («Він» for «Фінн»).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameSpelling {

    /** A capitalised word of a target: where a misspelt name stands. */
    public static final Pattern CAPITALISED = Pattern.compile("\\p{Lu}[\\p{L}\\p{M}'’ʼ-]+");

    private static final Pattern LOWER_CASE = Pattern.compile("\\p{Ll}[\\p{L}\\p{M}'’ʼ-]*");
    private static final Pattern DOUBLED = Pattern.compile("(\\p{L})\\1");

    /**
     * Folds a word to the form two spellings of one name share.
     *
     * @param word the non-null word
     * @param voicingPairs the non-null two-letter voicing pairs of the target language; the first letter becomes the second
     * @return the lower-case word with doubled letters collapsed and each pair folded
     */
    public static String fold(final String word, final List<String> voicingPairs) {
        Objects.requireNonNull(word, "word");
        Objects.requireNonNull(voicingPairs, "voicingPairs");
        String folded = DOUBLED.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("$1");
        for (final String pair : voicingPairs) {
            folded = folded.replace(pair.charAt(0), pair.charAt(1));
        }
        return folded;
    }

    /**
     * The words the texts write with a lower-case first letter.
     *
     * @param texts the non-null texts
     * @return the lower-cased words; never null
     */
    public static Set<String> lowerCaseWords(final Iterable<String> texts) {
        Objects.requireNonNull(texts, "texts");
        final Set<String> words = new HashSet<>();
        for (final String text : texts) {
            LOWER_CASE.matcher(text).results().forEach(found -> words.add(found.group()));
        }
        return words;
    }

    /**
     * Whether a word is a common word rather than a possible name.
     *
     * @param word the non-null word as written
     * @param targetLanguage the non-null target language tag, whose pronouns and stop words count
     * @param lowerCaseWords words the texts also write in lower case, as {@link #lowerCaseWords} returns them
     * @return {@code true} if the word is a pronoun, a stop word or written in lower case elsewhere, {@code false}
     *     otherwise
     */
    public static boolean isCommonWord(
            final String word, final String targetLanguage, final Set<String> lowerCaseWords) {
        final String lower = word.toLowerCase(Locale.ROOT);
        return lowerCaseWords.contains(lower)
                || StopWords.of(targetLanguage).contains(lower)
                || Pronouns.female(targetLanguage).contains(lower)
                || Pronouns.male(targetLanguage).contains(lower)
                || Pronouns.femaleObject(targetLanguage).contains(lower)
                || Pronouns.maleObject(targetLanguage).contains(lower);
    }
}
