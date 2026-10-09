package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.glossary.PronounReader.Pronoun;

/**
 * A character's gender read from the book itself: the first third-person pronoun after each mention of the name, in
 * the same or the next sentence and outside quoted speech, is that gender's evidence. A pronoun behind another name
 * is skipped, since it may belong to that name; the narrator's own first-person pronoun ({@code I}) is no such name.
 * Speech in straight quotes is blanked too when the paragraph's straight quotes pair up, since a speaker's {@code he}
 * is about somebody else. The pronouns come from the language file ({@code femalePronouns}, {@code malePronouns}, and
 * the weaker object and possessive forms {@code femaleObjectPronouns}, {@code maleObjectPronouns}); a language with no
 * subject pronouns is never read, silently. A suggestion needs {@link #MIN_EVIDENCE} pronouns of which
 * {@link #MIN_SHARE} agree, and it is marked as a suggestion like the first-name list's, so the person's confirmation
 * or edit always wins. A strong case ({@link Evidence#isStrong()}) needs no model at all.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PronounGender {

    /** The fewest pronouns a suggestion rests on; with fewer subject pronouns the object forms are read too. */
    static final int MIN_EVIDENCE = 3;

    /** The share of the pronouns that must name one gender. */
    static final double MIN_SHARE = 0.8;

    /** The fewest pronouns a gender is decided on without asking the model (15h.A2). */
    static final int STRONG_EVIDENCE = 5;

    /** The share of the pronouns that must agree for a gender decided without the model. */
    static final double STRONG_SHARE = 0.9;

    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * The pronouns seen after a name. The object and possessive forms count only while the subject pronouns are fewer
     * than {@link #MIN_EVIDENCE} and none of those names the other gender, since an object form often names somebody
     * else ("Tiger hit him").
     *
     * @param femaleWords the feminine subject pronouns, one per mention that had one, as written in lower case
     * @param maleWords the masculine subject pronouns, one per mention that had one
     * @param femaleObjectWords the feminine object or possessive forms, one per mention that had one
     * @param maleObjectWords the masculine object or possessive forms, one per mention that had one
     */
    public record Evidence(
            List<String> femaleWords,
            List<String> maleWords,
            List<String> femaleObjectWords,
            List<String> maleObjectWords) {

        /** Copies the lists. */
        public Evidence {
            femaleWords = List.copyOf(femaleWords);
            maleWords = List.copyOf(maleWords);
            femaleObjectWords = List.copyOf(femaleObjectWords);
            maleObjectWords = List.copyOf(maleObjectWords);
        }

        /** Evidence of subject pronouns only. */
        public Evidence(final List<String> femaleWords, final List<String> maleWords) {
            this(femaleWords, maleWords, List.of(), List.of());
        }

        /**
         * This evidence with another's added, for one person known by two names.
         *
         * @param other the non-null evidence of the other name
         * @return the lists of both, this one's first
         */
        public Evidence and(final Evidence other) {
            Objects.requireNonNull(other, "other");
            return new Evidence(
                    joined(femaleWords, other.femaleWords()),
                    joined(maleWords, other.maleWords()),
                    joined(femaleObjectWords, other.femaleObjectWords()),
                    joined(maleObjectWords, other.maleObjectWords()));
        }

        private static List<String> joined(final List<String> first, final List<String> second) {
            final List<String> both = new ArrayList<>(first);
            both.addAll(second);
            return both;
        }

        /**
         * Whether the object and possessive forms are read: too few subject pronouns, and none contradicts them.
         *
         * @return {@code true} if the object forms count, {@code false} otherwise
         */
        public boolean usesObjectForms() {
            final boolean contradicted = (!femaleObjectWords.isEmpty() && !maleWords.isEmpty())
                    || (!maleObjectWords.isEmpty() && !femaleWords.isEmpty());
            return femaleWords.size() + maleWords.size() < MIN_EVIDENCE && !contradicted;
        }

        /**
         * The feminine pronouns that count.
         *
         * @return the feminine subject pronouns, plus the object forms when {@link #usesObjectForms()}
         */
        public int female() {
            return femaleWords.size() + (usesObjectForms() ? femaleObjectWords.size() : 0);
        }

        /**
         * The masculine pronouns that count.
         *
         * @return the masculine subject pronouns, plus the object forms when {@link #usesObjectForms()}
         */
        public int male() {
            return maleWords.size() + (usesObjectForms() ? maleObjectWords.size() : 0);
        }

        /**
         * All pronouns that count.
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
            return dominantBy(MIN_EVIDENCE, MIN_SHARE);
        }

        /**
         * Whether the evidence decides the gender with no model: {@link #STRONG_EVIDENCE} or more pronouns of which
         * {@link #STRONG_SHARE} agree.
         *
         * @return {@code true} if the dominant gender needs no model's judgement, {@code false} otherwise
         */
        public boolean isStrong() {
            return dominantBy(STRONG_EVIDENCE, STRONG_SHARE).isPresent();
        }

        private Optional<Gender> dominantBy(final int fewest, final double share) {
            if (total() < fewest) {
                return Optional.empty();
            }
            if ((double) female() / total() >= share) {
                return Optional.of(Gender.FEMALE);
            }
            return (double) male() / total() >= share ? Optional.of(Gender.MALE) : Optional.empty();
        }

        /**
         * The evidence as a few words for a prompt line, e.g. {@code she/her ×4}.
         *
         * @return each counted side's distinct pronouns and count, joined by commas; empty when there is none
         */
        public String compact() {
            return String.join(", ", parts(true, true));
        }

        /**
         * The evidence that agrees with a gender, for a line that already states it: a count that contradicts the set
         * gender would only make a model doubt it.
         *
         * @param gender the non-null gender the line states
         * @return that gender's distinct pronouns and count; empty for any other gender or when there is none
         */
        public String compactAgreeing(final Gender gender) {
            Objects.requireNonNull(gender, "gender");
            return String.join(", ", parts(gender == Gender.FEMALE, gender == Gender.MALE));
        }

        private List<String> parts(final boolean withFemale, final boolean withMale) {
            final boolean objects = usesObjectForms();
            final List<String> parts = new ArrayList<>();
            addPart(parts, withFemale, femaleWords);
            addPart(parts, withMale, maleWords);
            addPart(parts, withFemale && objects, femaleObjectWords);
            addPart(parts, withMale && objects, maleObjectWords);
            return parts;
        }

        private static void addPart(final List<String> parts, final boolean wanted, final List<String> words) {
            if (wanted && !words.isEmpty()) {
                parts.add(String.join("/", new LinkedHashSet<>(words)) + " ×" + words.size());
            }
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
        final PronounReader reader = PronounReader.of(term, languageTag == null ? DEFAULT_LANGUAGE : languageTag);
        if (reader == null) {
            log.trace("Pronoun evidence skipped term={} language={}: no pronoun table", term, languageTag);
            return new Evidence(List.of(), List.of());
        }
        final Tally tally = new Tally();
        texts.forEach(text -> reader.read(text).forEach(tally::add));
        return tally.evidence();
    }

    /** The running lists of one name's pronouns. */
    private record Tally(List<String> female, List<String> male, List<String> femaleObject, List<String> maleObject) {

        Tally() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        void add(final Mention mention) {
            final Pronoun subject = mention.subject();
            final Pronoun object = mention.object();
            if (subject != null) {
                (subject.gender() == Gender.FEMALE ? female : male).add(subject.word());
            }
            if (object != null) {
                (object.gender() == Gender.FEMALE ? femaleObject : maleObject).add(object.word());
            }
        }

        Evidence evidence() {
            return new Evidence(female, male, femaleObject, maleObject);
        }
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
}
