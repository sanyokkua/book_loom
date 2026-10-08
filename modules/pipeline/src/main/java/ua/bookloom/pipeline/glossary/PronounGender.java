package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.checks.QuotedSpans;
import ua.bookloom.pipeline.prompt.Pronouns;

/**
 * A character's gender read from the book itself: the first third-person pronoun after each mention of the name, in
 * the same or the next sentence and outside quoted speech, is that gender's evidence. A pronoun behind another name
 * is skipped, since it may belong to that name. The pronouns come from the language file ({@code femalePronouns},
 * {@code malePronouns}); a language with none is never read, silently. A suggestion needs
 * {@link #MIN_EVIDENCE} pronouns of which {@link #MIN_SHARE} agree, and it is marked as a suggestion like the
 * first-name list's, so the person's confirmation or edit always wins.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PronounGender {

    /** The fewest pronouns a suggestion rests on. */
    static final int MIN_EVIDENCE = 3;

    /** The share of the pronouns that must name one gender. */
    static final double MIN_SHARE = 0.8;

    private static final String DEFAULT_LANGUAGE = "en";
    private static final int WINDOW = 240;
    private static final int PREFILTER = 3;
    private static final Pattern TOKEN = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ]*|[.!?…]+");
    private static final Pattern TERMINATOR = Pattern.compile("[.!?…]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * The pronouns seen after a name.
     *
     * @param femaleWords the feminine pronouns, one per mention that had one, as written in lower case
     * @param maleWords the masculine pronouns, one per mention that had one
     */
    public record Evidence(List<String> femaleWords, List<String> maleWords) {

        /** Copies the lists. */
        public Evidence {
            femaleWords = List.copyOf(femaleWords);
            maleWords = List.copyOf(maleWords);
        }

        /**
         * The feminine pronouns seen.
         *
         * @return how many mentions were followed by a feminine pronoun
         */
        public int female() {
            return femaleWords.size();
        }

        /**
         * The masculine pronouns seen.
         *
         * @return how many mentions were followed by a masculine pronoun
         */
        public int male() {
            return maleWords.size();
        }

        /**
         * All pronouns seen.
         *
         * @return the feminine and masculine counts together
         */
        public int total() {
            return female() + male();
        }

        /**
         * The gender one side carries by enough evidence.
         *
         * @return the dominant gender, or empty when there is too little evidence or the sides are too mixed
         */
        public Optional<Gender> dominant() {
            if (total() < MIN_EVIDENCE) {
                return Optional.empty();
            }
            if ((double) female() / total() >= MIN_SHARE) {
                return Optional.of(Gender.FEMALE);
            }
            return (double) male() / total() >= MIN_SHARE ? Optional.of(Gender.MALE) : Optional.empty();
        }

        /**
         * The evidence as a few words for a prompt line, e.g. {@code she/her ×4}.
         *
         * @return each side's distinct pronouns and count, joined by commas; empty when there is none
         */
        public String compact() {
            final List<String> parts = new ArrayList<>();
            if (female() > 0) {
                parts.add(String.join("/", new LinkedHashSet<>(femaleWords)) + " ×" + female());
            }
            if (male() > 0) {
                parts.add(String.join("/", new LinkedHashSet<>(maleWords)) + " ×" + male());
            }
            return String.join(", ", parts);
        }
    }

    /** The pronoun tables of one language. */
    private record Tables(Set<String> female, Set<String> male) {

        boolean isEmpty() {
            return female.isEmpty() || male.isEmpty();
        }

        boolean contains(final String word) {
            return female.contains(word) || male.contains(word);
        }
    }

    /**
     * Counts the pronouns that follow a name.
     *
     * @param term the non-blank glossary term
     * @param texts the non-null visible texts to read, tokens already removed
     * @param languageTag the source language's tag, or null for English
     * @return the pronouns seen; empty evidence when the language lists no pronouns
     */
    public static Evidence of(final String term, final List<String> texts, @Nullable final String languageTag) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(texts, "texts");
        final String language = languageTag == null ? DEFAULT_LANGUAGE : languageTag;
        final Tables tables = new Tables(Pronouns.female(language), Pronouns.male(language));
        if (term.isBlank() || tables.isEmpty()) {
            log.trace("Pronoun evidence skipped term={} language={}: no pronoun table", term, language);
            return new Evidence(List.of(), List.of());
        }
        final Pattern name = namePattern(term);
        final String first = SPACES.splitAsStream(term.strip()).findFirst().orElse(term);
        final String probe = first.substring(0, Math.min(first.length(), PREFILTER));
        final List<String> femaleSeen = new ArrayList<>();
        final List<String> maleSeen = new ArrayList<>();
        for (final String text : texts) {
            if (text.contains(probe)) {
                readText(QuotedSpans.narration(text, language), name, tables, femaleSeen, maleSeen);
            }
        }
        return new Evidence(femaleSeen, maleSeen);
    }

    /**
     * The entry as the book's pronouns leave it: a person's entry, or a term or untyped entry, that has no gender and
     * was never tried gets the dominant gender as a suggestion, and a term entry becomes a character. Nothing a person
     * set, nothing locked and no place or title is touched.
     *
     * @param entry the non-null entry
     * @param texts the non-null visible texts of the book
     * @param languageTag the source language's tag, or null for English
     * @return the entry with a suggested gender, or {@code entry} itself when nothing is to be suggested
     */
    public static GlossaryEntry seeded(
            final GlossaryEntry entry, final List<String> texts, @Nullable final String languageTag) {
        Objects.requireNonNull(entry, "entry");
        final boolean eligible = entry.gender() == Gender.UNKNOWN
                && !entry.locked()
                && !entry.genderSeedTried()
                && isPersonLike(entry.type());
        if (!eligible) {
            return entry;
        }
        final Evidence evidence = of(entry.term(), texts, languageTag);
        final Optional<Gender> dominant = evidence.dominant();
        if (dominant.isEmpty()) {
            log.debug(
                    "Pronoun evidence for entry {}: {} female, {} male, too little or too mixed",
                    entry.id(),
                    evidence.female(),
                    evidence.male());
            return entry;
        }
        log.debug(
                "Glossary entry {} gets the suggested gender {} from pronouns ({} female, {} male)",
                entry.id(),
                dominant.get(),
                evidence.female(),
                evidence.male());
        return entry.withType(TermType.CHARACTER).withSuggestedGender(dominant.get());
    }

    /**
     * The entries as {@link #seeded(GlossaryEntry, List, String)} would leave them, in order.
     *
     * @param entries the non-null entries
     * @param texts the non-null visible texts of the book
     * @param languageTag the source language's tag, or null for English
     * @return a list of the same size; never null
     */
    public static List<GlossaryEntry> seeded(
            final List<GlossaryEntry> entries, final List<String> texts, @Nullable final String languageTag) {
        Objects.requireNonNull(entries, "entries");
        return entries.stream().map(entry -> seeded(entry, texts, languageTag)).toList();
    }

    /**
     * Writes the suggestions to the held entries, each in one atomic step against its current state so a person's edit
     * made meanwhile is not overwritten.
     *
     * @param glossary the non-null glossary to read and update
     * @param projectId the non-null project
     * @param texts the non-null visible texts of the book
     * @param languageTag the source language's tag, or null for English
     * @return how many entries changed, or the repository's error
     */
    public static Result<Integer> seedHeld(
            final GlossaryRepository glossary,
            final String projectId,
            final List<String> texts,
            @Nullable final String languageTag) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(projectId, "projectId");
        return glossary.all(projectId).flatMap(held -> {
            int changed = 0;
            for (final GlossaryEntry entry : held) {
                if (seeded(entry, texts, languageTag).equals(entry)) {
                    continue;
                }
                final Result<Optional<GlossaryEntry>> stored =
                        glossary.update(projectId, entry.term(), current -> seeded(current, texts, languageTag));
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
                changed++;
            }
            log.info("Pronoun gender seeding project={} language={} entriesSeeded={}", projectId, languageTag, changed);
            return Result.ok(changed);
        });
    }

    private static boolean isPersonLike(final TermType type) {
        return type == TermType.CHARACTER || type == TermType.TERM || type == TermType.OTHER;
    }

    private static void readText(
            final String narration,
            final Pattern name,
            final Tables tables,
            final List<String> femaleSeen,
            final List<String> maleSeen) {
        final Matcher mention = name.matcher(narration);
        while (mention.find()) {
            final String window =
                    narration.substring(mention.end(), Math.min(narration.length(), mention.end() + WINDOW));
            final String pronoun = firstPronoun(window, tables);
            if (pronoun != null) {
                (tables.female().contains(pronoun) ? femaleSeen : maleSeen).add(pronoun);
            }
        }
    }

    // The first pronoun of the rest of this sentence and the next one, unless a capitalised word that does not open
    // a sentence comes first: that is another name, and the pronoun may be its.
    private static @Nullable String firstPronoun(final String window, final Tables tables) {
        final Matcher token = TOKEN.matcher(window);
        int terminators = 0;
        boolean sentenceStart = false;
        while (token.find()) {
            final String word = token.group();
            if (TERMINATOR.matcher(word).matches()) {
                terminators++;
                sentenceStart = true;
                if (terminators > 1) {
                    return null;
                }
                continue;
            }
            final String lower = word.toLowerCase(Locale.ROOT);
            if (tables.contains(lower)) {
                return lower;
            }
            if (Character.isUpperCase(word.codePointAt(0)) && !sentenceStart) {
                return null;
            }
            sentenceStart = false;
        }
        return null;
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
