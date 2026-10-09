package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.checks.QuotedSpans;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.Pronouns;

/**
 * Reads the pronouns that follow each mention of one name in a language: the first third-person pronoun of the rest of
 * the sentence and the next, outside quoted speech, unless another name comes first. Speech in straight quotes is
 * blanked when the paragraph's straight quotes pair up, since a speaker's {@code he} is about somebody else, and the
 * narrator's own first-person pronoun ({@code I}) is no other name. An object or possessive form seen on the way is
 * kept apart, as weaker evidence ({@link PronounGender.Evidence}).
 */
final class PronounReader {

    private static final int WINDOW = 240;
    private static final int PREFILTER = 3;
    private static final char STRAIGHT_QUOTE = '"';
    private static final Pattern TOKEN = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ]*|[.!?…]+");
    private static final Pattern TERMINATOR = Pattern.compile("[.!?…]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Pattern name;
    private final String probe;
    private final Tables tables;
    private final String language;

    private PronounReader(final Pattern name, final String probe, final Tables tables, final String language) {
        this.name = name;
        this.probe = probe;
        this.tables = tables;
        this.language = language;
    }

    /**
     * A reader of one name.
     *
     * @param term the glossary term
     * @param language the non-null language tag whose pronouns are read
     * @return the reader, or null when the term is blank or the language lists no subject pronouns
     */
    static @Nullable PronounReader of(final String term, final String language) {
        final Tables tables = Tables.of(language);
        if (term.isBlank() || tables.isEmpty()) {
            return null;
        }
        return new PronounReader(namePattern(term), probeOf(term), tables, language);
    }

    /**
     * A pronoun read after one mention.
     *
     * @param word the pronoun in lower case
     * @param gender the gender it shows, {@link Gender#FEMALE} or {@link Gender#MALE}
     * @param end where it ends in the text the mention was read from
     */
    record Pronoun(String word, Gender gender, int end) {}

    /**
     * One mention of a name in narration, with the pronouns that follow it.
     *
     * @param start where the name starts in the text
     * @param subject the first subject pronoun after it, or null when none can be its
     * @param object the first object or possessive form after it and before any subject pronoun, or null
     */
    record Mention(
            int start, @Nullable Pronoun subject, @Nullable Pronoun object) {

        /**
         * The pronoun this mention gives its name: the subject pronoun, else the object form.
         *
         * @return the pronoun, or null when the mention has none
         */
        @Nullable
        Pronoun attributed() {
            return subject != null ? subject : object;
        }
    }

    /** The pronoun tables of one language. */
    private record Tables(
            Set<String> female,
            Set<String> male,
            Set<String> femaleObject,
            Set<String> maleObject,
            Set<String> firstPerson) {

        static Tables of(final String language) {
            return new Tables(
                    Pronouns.female(language),
                    Pronouns.male(language),
                    Pronouns.femaleObject(language),
                    Pronouns.maleObject(language),
                    LanguageRules.bundled().firstPersonPronouns(language));
        }

        boolean isEmpty() {
            return female.isEmpty() || male.isEmpty();
        }

        @Nullable
        Gender subjectGender(final String lower) {
            return genderIn(lower, female, male);
        }

        @Nullable
        Gender objectGender(final String lower) {
            return genderIn(lower, femaleObject, maleObject);
        }

        private static @Nullable Gender genderIn(
                final String lower, final Set<String> feminine, final Set<String> masculine) {
            if (feminine.contains(lower)) {
                return Gender.FEMALE;
            }
            return masculine.contains(lower) ? Gender.MALE : null;
        }
    }

    List<Mention> read(final String text) {
        if (!text.contains(probe)) {
            return List.of();
        }
        final String narration = QuotedSpans.narration(withoutStraightSpeech(text), language);
        final List<Mention> mentions = new ArrayList<>();
        final Matcher mention = name.matcher(narration);
        while (mention.find()) {
            mentions.add(mentionAfter(narration, mention.start(), mention.end()));
        }
        return mentions;
    }

    // The first pronoun of the rest of this sentence and the next one, unless a capitalised word that does not open
    // a sentence comes first: that is another name, and the pronoun may be its. The narrator's "I" is not one. An
    // object form seen on the way is kept apart, as weaker evidence.
    private Mention mentionAfter(final String narration, final int start, final int end) {
        final String window = narration.substring(end, Math.min(narration.length(), end + WINDOW));
        final Matcher token = TOKEN.matcher(window);
        int terminators = 0;
        boolean sentenceStart = false;
        Pronoun object = null;
        while (token.find()) {
            final String word = token.group();
            if (TERMINATOR.matcher(word).matches()) {
                terminators++;
                sentenceStart = true;
                if (terminators > 1) {
                    break;
                }
                continue;
            }
            final String lower = word.toLowerCase(Locale.ROOT);
            final Gender subject = tables.subjectGender(lower);
            if (subject != null) {
                return new Mention(start, new Pronoun(lower, subject, end + token.end()), object);
            }
            object = object == null ? objectOf(lower, end + token.end()) : object;
            if (isAnotherName(word, sentenceStart)) {
                break;
            }
            sentenceStart = false;
        }
        return new Mention(start, null, object);
    }

    private @Nullable Pronoun objectOf(final String lower, final int end) {
        final Gender gender = tables.objectGender(lower);
        return gender == null ? null : new Pronoun(lower, gender, end);
    }

    private boolean isAnotherName(final String word, final boolean sentenceStart) {
        return Character.isUpperCase(word.codePointAt(0))
                && !sentenceStart
                && !tables.firstPerson().contains(word);
    }

    /**
     * The text with the speech between straight quotes blanked, when the paragraph's straight quotes pair up; with an
     * odd number nobody can tell speech from narration, so the text is read as it is. The length is kept.
     */
    static String withoutStraightSpeech(final String text) {
        final long quotes = text.chars().filter(c -> c == STRAIGHT_QUOTE).count();
        if (quotes == 0 || quotes % 2 != 0) {
            return text;
        }
        final char[] chars = text.toCharArray();
        boolean inside = false;
        for (int index = 0; index < chars.length; index++) {
            final boolean quote = chars[index] == STRAIGHT_QUOTE;
            inside = quote != inside;
            if (quote || inside) {
                chars[index] = ' ';
            }
        }
        return new String(chars);
    }

    private static String probeOf(final String term) {
        final String first = SPACES.splitAsStream(term.strip()).findFirst().orElse(term);
        return first.substring(0, Math.min(first.length(), PREFILTER));
    }

    private static Pattern namePattern(final String term) {
        final String stripped = term.strip();
        final boolean latinEnd = Character.UnicodeScript.of(stripped.codePointBefore(stripped.length()))
                == Character.UnicodeScript.LATIN;
        final String ending = latinEnd ? "(?:['’ʼ]s)?" : "\\p{L}{0,3}";
        return Pattern.compile("(?<![\\p{L}\\p{N}])"
                + SPACES.splitAsStream(stripped).map(Pattern::quote).collect(Collectors.joining("\\s+"))
                + ending
                + "(?![\\p{L}\\p{N}])");
    }
}
