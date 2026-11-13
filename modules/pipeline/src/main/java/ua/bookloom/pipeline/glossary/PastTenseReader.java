package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.checks.QuotedSpans;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Reads the gender a past-tense verb shows, one reader for every language, from the language file: the endings
 * {@code pastFemale}, {@code pastMale} and {@code pastNeuter}, the irregular masculine forms {@code pastMaleIrregular}
 * (matched as endings, so a prefixed {@code смог} is read as {@code мог}), the short adjectives {@code shortAdjFemale}
 * and {@code shortAdjMale} (exact words), the words that only look like a past tense ({@code pastNotVerbs}) and the
 * particles and adverbs that may stand between a subject and its verb ({@code pastSkipWords}). A language whose file
 * lists no feminine or masculine past endings has no reader.
 *
 * <p>It gives two kinds of evidence: the first past verb after a mention of a name, in the same clause, outside speech
 * and with no other name between ({@link #voteAfter}); and the narrator's own first-person past forms in narration
 * ({@link #firstPerson}).
 */
@Slf4j
public final class PastTenseReader {

    /** The fewest letters a word must have to be read as a past form, so a bare ending is never a verb. */
    static final int MIN_VERB_LENGTH = 3;

    /** How many particles or adverbs may stand between a subject and its verb. */
    static final int MAX_SKIPPED = 3;

    /** The fewest first-person forms a narrator's gender is verified on. */
    public static final int MIN_FORMS = 3;

    /** The share of the forms that must agree, inclusive. */
    public static final double MIN_SHARE = 0.8;

    /** How many of the narrator's own forms are kept as quotes. */
    static final int MAX_QUOTES = 3;

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");
    private static final Pattern BREAK = Pattern.compile("[,.;:!?…()\\[\\]—–―\"«»“”„]");
    private static final Map<String, Optional<PastTenseReader>> READERS = new ConcurrentHashMap<>();

    private final String language;
    private final List<String> female;
    private final List<String> male;
    private final List<String> neuter;
    private final List<String> maleIrregular;
    private final Set<String> shortFemale;
    private final Set<String> shortMale;
    private final Set<String> notVerbs;
    private final Set<String> skipped;
    private final Set<String> firstPersonPronouns;

    private PastTenseReader(final String language, final LanguageRules rules) {
        this.language = language;
        this.female = rules.wordsOf(language, "pastFemale");
        this.male = rules.wordsOf(language, "pastMale");
        this.neuter = rules.wordsOf(language, "pastNeuter");
        this.maleIrregular = rules.wordsOf(language, "pastMaleIrregular");
        this.shortFemale = lowerSet(rules.wordsOf(language, "shortAdjFemale"));
        this.shortMale = lowerSet(rules.wordsOf(language, "shortAdjMale"));
        this.notVerbs = lowerSet(rules.wordsOf(language, "pastNotVerbs"));
        this.skipped = lowerSet(rules.wordsOf(language, "pastSkipWords"));
        this.firstPersonPronouns = Set.copyOf(rules.firstPersonPronouns(language));
    }

    /**
     * One past verb after a mention.
     *
     * @param gender {@link Gender#FEMALE} or {@link Gender#MALE}
     * @param word the verb in lower case
     * @param start where the verb starts in the text
     * @param end where it ends
     */
    public record Vote(Gender gender, String word, int start, int end) {}

    /**
     * The narrator's own first-person past forms.
     *
     * @param female how many feminine forms were counted
     * @param male how many masculine forms were counted
     * @param femaleQuotes the first feminine forms as written (the pronoun up to the verb), at most {@link #MAX_QUOTES}
     * @param maleQuotes the first masculine forms as written, at most {@link #MAX_QUOTES}
     */
    public record FirstPersonCount(int female, int male, List<String> femaleQuotes, List<String> maleQuotes) {

        /** No forms at all. */
        public static final FirstPersonCount NONE = new FirstPersonCount(0, 0, List.of(), List.of());

        /** Copies the quotes. */
        public FirstPersonCount {
            femaleQuotes = List.copyOf(femaleQuotes);
            maleQuotes = List.copyOf(maleQuotes);
        }

        /**
         * All forms counted.
         *
         * @return the feminine and masculine forms together
         */
        public int forms() {
            return female + male;
        }

        /**
         * The gender most forms show.
         *
         * @return {@link Gender#FEMALE} or {@link Gender#MALE}; {@link Gender#UNKNOWN} when there is no form or the
         *     sides are even
         */
        public Gender gender() {
            if (female == male) {
                return Gender.UNKNOWN;
            }
            return female > male ? Gender.FEMALE : Gender.MALE;
        }

        /**
         * How many forms show {@link #gender()}.
         *
         * @return the larger side's count
         */
        public int agreeing() {
            return Math.max(female, male);
        }

        /**
         * Whether the count settles the gender: at least {@link #MIN_FORMS} forms, {@link #MIN_SHARE} of them agreeing.
         *
         * @return {@code true} if the gender is verified by the book, {@code false} otherwise
         */
        public boolean verified() {
            return forms() >= MIN_FORMS && gender() != Gender.UNKNOWN && agreeing() >= MIN_SHARE * forms();
        }

        /**
         * The quotes of the forms that show {@link #gender()}.
         *
         * @return never null; empty when no gender is shown
         */
        public List<String> quotes() {
            return switch (gender()) {
                case FEMALE -> femaleQuotes;
                case MALE -> maleQuotes;
                default -> List.of();
            };
        }

        /**
         * This count with another's added, the quotes of this one first.
         *
         * @param other the non-null count of more texts
         * @return the sums, each side keeping at most {@link #MAX_QUOTES} quotes
         */
        public FirstPersonCount plus(final FirstPersonCount other) {
            Objects.requireNonNull(other, "other");
            return new FirstPersonCount(
                    female + other.female(),
                    male + other.male(),
                    firstOf(femaleQuotes, other.femaleQuotes()),
                    firstOf(maleQuotes, other.maleQuotes()));
        }

        private static List<String> firstOf(final List<String> first, final List<String> second) {
            final List<String> both = new ArrayList<>(first);
            both.addAll(second);
            return List.copyOf(both.subList(0, Math.min(MAX_QUOTES, both.size())));
        }
    }

    /**
     * The reader of a language.
     *
     * @param language the language tag, or null when it is not known
     * @return the reader, or null when the language is not known or its file lists no feminine or masculine past forms
     */
    public static @Nullable PastTenseReader of(@Nullable final String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return READERS.computeIfAbsent(language, PastTenseReader::load).orElse(null);
    }

    private static Optional<PastTenseReader> load(final String language) {
        final PastTenseReader reader = new PastTenseReader(language, LanguageRules.bundled());
        final boolean usable = !reader.female.isEmpty() && !reader.male.isEmpty();
        log.debug(
                "Past-tense reader language={} usable={} endings={}/{}/{} skipWords={} notVerbs={}",
                language,
                usable,
                reader.female.size(),
                reader.male.size(),
                reader.neuter.size(),
                reader.skipped.size(),
                reader.notVerbs.size());
        return usable ? Optional.of(reader) : Optional.empty();
    }

    /**
     * The gender a word shows as a past-tense verb or a short adjective.
     *
     * @param lowerWord the non-null word in lower case
     * @return the gender, {@link Gender#NEUTER} for a neuter form; empty when the word is no past form
     */
    public Optional<Gender> genderOf(final String lowerWord) {
        Objects.requireNonNull(lowerWord, "lowerWord");
        if (shortFemale.contains(lowerWord)) {
            return Optional.of(Gender.FEMALE);
        }
        if (shortMale.contains(lowerWord)) {
            return Optional.of(Gender.MALE);
        }
        if (lowerWord.length() < MIN_VERB_LENGTH || notVerbs.contains(lowerWord)) {
            return Optional.empty();
        }
        final int feminine = longestEnding(lowerWord, female);
        final int masculine = Math.max(longestEnding(lowerWord, male), longestEnding(lowerWord, maleIrregular));
        final int neutral = longestEnding(lowerWord, neuter);
        final int longest = Math.max(feminine, Math.max(masculine, neutral));
        if (longest == 0) {
            return Optional.empty();
        }
        if (longest == feminine) {
            return Optional.of(Gender.FEMALE);
        }
        return Optional.of(longest == neutral ? Gender.NEUTER : Gender.MALE);
    }

    /**
     * Whether a word may stand between a subject and its past verb ({@code pastSkipWords}).
     *
     * @param lowerWord the non-null word in lower case
     * @return {@code true} if the word is a particle or an adverb the file lists, {@code false} otherwise
     */
    public boolean isSkipped(final String lowerWord) {
        return skipped.contains(Objects.requireNonNull(lowerWord, "lowerWord"));
    }

    /**
     * The first past verb after a mention of a name, read in narration: past the particles and adverbs of
     * {@code pastSkipWords}, never past punctuation, a capitalised word (another name) or a word that is no past form.
     *
     * @param text the non-null text that holds the mention
     * @param mentionStart where the mention starts
     * @param mentionEnd where it ends
     * @return the feminine or masculine vote, or empty when the clause holds none (a neuter or plural verb is none)
     */
    public Optional<Vote> voteAfter(final String text, final int mentionStart, final int mentionEnd) {
        Objects.requireNonNull(text, "text");
        final String narration = QuotedSpans.narration(text, language);
        if (mentionStart < 0 || mentionEnd > narration.length() || narration.charAt(mentionStart) == ' ') {
            return Optional.empty();
        }
        return pastAfter(narration, mentionEnd, true)
                .filter(vote -> vote.gender() == Gender.FEMALE || vote.gender() == Gender.MALE);
    }

    /**
     * Counts the narrator's own past forms in narration: a first-person pronoun of {@code firstPersonPronouns}, up to
     * {@link #MAX_SKIPPED} words of {@code pastSkipWords}, then a past form. Quoted speech and dash dialogue are not
     * read.
     *
     * @param texts the non-null texts to read, formatting tokens already removed
     * @return the count; {@link FirstPersonCount#NONE} when the language lists no first-person pronoun
     */
    public FirstPersonCount firstPerson(final List<String> texts) {
        Objects.requireNonNull(texts, "texts");
        FirstPersonCount count = FirstPersonCount.NONE;
        for (final String text : texts) {
            count = count.plus(firstPersonIn(text));
        }
        log.debug(
                "First-person past forms language={} texts={} female={} male={} verified={}",
                language,
                texts.size(),
                count.female(),
                count.male(),
                count.verified());
        return count;
    }

    private FirstPersonCount firstPersonIn(final String text) {
        if (firstPersonPronouns.isEmpty()) {
            return FirstPersonCount.NONE;
        }
        final String narration = QuotedSpans.narration(text, language);
        final List<String> femaleQuotes = new ArrayList<>();
        final List<String> maleQuotes = new ArrayList<>();
        int feminine = 0;
        int masculine = 0;
        final Matcher word = WORD.matcher(narration);
        while (word.find()) {
            if (!firstPersonPronouns.contains(word.group())) {
                continue;
            }
            final Optional<Vote> verb = pastAfter(narration, word.end(), false);
            if (verb.isPresent() && verb.get().gender() == Gender.FEMALE) {
                feminine++;
                femaleQuotes.add(text.substring(word.start(), verb.get().end()));
            } else if (verb.isPresent() && verb.get().gender() == Gender.MALE) {
                masculine++;
                maleQuotes.add(text.substring(word.start(), verb.get().end()));
            }
        }
        return new FirstPersonCount(
                feminine,
                masculine,
                femaleQuotes.subList(0, Math.min(MAX_QUOTES, femaleQuotes.size())),
                maleQuotes.subList(0, Math.min(MAX_QUOTES, maleQuotes.size())));
    }

    // Past the skip words to the first other word of the clause; a capitalised one stops when stopAtNames is set.
    private Optional<Vote> pastAfter(final String narration, final int from, final boolean stopAtNames) {
        int position = from;
        for (int skippedWords = 0; skippedWords <= MAX_SKIPPED; skippedWords++) {
            final Matcher word = WORD.matcher(narration);
            if (!word.find(position)
                    || BREAK.matcher(narration.substring(position, word.start()))
                            .find()) {
                return Optional.empty();
            }
            final String written = word.group();
            final String lower = written.toLowerCase(Locale.ROOT);
            if (skipped.contains(lower)) {
                position = word.end();
                continue;
            }
            if (stopAtNames && Character.isUpperCase(written.codePointAt(0))) {
                return Optional.empty();
            }
            return genderOf(lower).map(gender -> new Vote(gender, lower, word.start(), word.end()));
        }
        return Optional.empty();
    }

    private static int longestEnding(final String lower, final List<String> endings) {
        return endings.stream()
                .filter(ending -> lower.length() > ending.length() && lower.endsWith(ending))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private static Set<String> lowerSet(final List<String> words) {
        return words.stream().map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
