package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * A character's gender read from the book itself: the first third-person pronoun after each mention of the name, in
 * the same or the next sentence and outside quoted speech, is that gender's evidence. A pronoun behind another name
 * is skipped, since it may belong to that name; the narrator's own first-person pronoun ({@code I}) is no such name.
 * Speech in straight quotes is blanked too when the paragraph's straight quotes pair up, since a speaker's {@code he}
 * is about somebody else. The pronouns come from the language file ({@code femalePronouns}, {@code malePronouns}, and
 * the weaker object and possessive forms {@code femaleObjectPronouns}, {@code maleObjectPronouns}); a language with no
 * subject pronouns is never read, silently. The first past-tense verb after a mention written as the term
 * ({@link PastTenseReader}) is a vote too. A suggestion needs {@link #MIN_EVIDENCE} votes of which {@link #MIN_SHARE}
 * agree, and it is marked as likely, so the person's confirmation or edit always wins; a strong case
 * ({@link Evidence#isStrong()}) needs no model at all and is the book's.
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

    /**
     * The pronouns and past-tense verbs seen after a name. The object and possessive forms count only while the subject
     * pronouns and verbs are fewer than {@link #MIN_EVIDENCE} and none of those names the other gender, since an object
     * form often names somebody else ("Badger hit him").
     *
     * @param femaleWords the feminine subject pronouns, one per mention that had one, as written in lower case
     * @param maleWords the masculine subject pronouns, one per mention that had one
     * @param femaleObjectWords the feminine object or possessive forms, one per mention that had one
     * @param maleObjectWords the masculine object or possessive forms, one per mention that had one
     * @param femaleVerbs the feminine past-tense verbs right after the name itself ({@link PastTenseReader#voteAfter})
     * @param maleVerbs the masculine past-tense verbs right after the name itself
     */
    public record Evidence(
            List<String> femaleWords,
            List<String> maleWords,
            List<String> femaleObjectWords,
            List<String> maleObjectWords,
            List<String> femaleVerbs,
            List<String> maleVerbs) {

        /** Copies the lists. */
        public Evidence {
            femaleWords = List.copyOf(femaleWords);
            maleWords = List.copyOf(maleWords);
            femaleObjectWords = List.copyOf(femaleObjectWords);
            maleObjectWords = List.copyOf(maleObjectWords);
            femaleVerbs = List.copyOf(femaleVerbs);
            maleVerbs = List.copyOf(maleVerbs);
        }

        /** Evidence of pronouns only. */
        public Evidence(
                final List<String> femaleWords,
                final List<String> maleWords,
                final List<String> femaleObjectWords,
                final List<String> maleObjectWords) {
            this(femaleWords, maleWords, femaleObjectWords, maleObjectWords, List.of(), List.of());
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
                    joined(maleObjectWords, other.maleObjectWords()),
                    joined(femaleVerbs, other.femaleVerbs()),
                    joined(maleVerbs, other.maleVerbs()));
        }

        private static List<String> joined(final List<String> first, final List<String> second) {
            final List<String> both = new ArrayList<>(first);
            both.addAll(second);
            return both;
        }

        /**
         * Whether the object and possessive forms are read: too few subject pronouns and verbs, and none contradicts
         * them.
         *
         * @return {@code true} if the object forms count, {@code false} otherwise
         */
        public boolean usesObjectForms() {
            final int feminine = femaleWords.size() + femaleVerbs.size();
            final int masculine = maleWords.size() + maleVerbs.size();
            final boolean contradicted =
                    (!femaleObjectWords.isEmpty() && masculine > 0) || (!maleObjectWords.isEmpty() && feminine > 0);
            return feminine + masculine < MIN_EVIDENCE && !contradicted;
        }

        /**
         * The feminine votes that count.
         *
         * @return the feminine subject pronouns and verbs, plus the object forms when {@link #usesObjectForms()}
         */
        public int female() {
            return femaleWords.size() + femaleVerbs.size() + (usesObjectForms() ? femaleObjectWords.size() : 0);
        }

        /**
         * The masculine votes that count.
         *
         * @return the masculine subject pronouns and verbs, plus the object forms when {@link #usesObjectForms()}
         */
        public int male() {
            return maleWords.size() + maleVerbs.size() + (usesObjectForms() ? maleObjectWords.size() : 0);
        }

        /**
         * All votes that count.
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
         * The evidence as a few words for a prompt line, e.g. {@code she/her ×4} or {@code пришла/ушла ×2}.
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
            addPart(parts, withFemale, femaleVerbs);
            addPart(parts, withMale, maleVerbs);
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
     * Counts the pronouns that follow a name in some of the book's segments, from the book's index.
     *
     * @param term the non-blank glossary term
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null
     * @param languageTag the source language's tag, or null when it is not known (no pronoun table, no evidence)
     * @return the pronouns seen; never null
     */
    public static Evidence of(
            final String term,
            final BookIndex index,
            final List<Integer> ordinals,
            @Nullable final String languageTag) {
        Objects.requireNonNull(index, "index");
        return of(term, index.visibleTexts(ordinals), languageTag);
    }

    /**
     * Counts the pronouns that follow a name.
     *
     * @param term the non-blank glossary term
     * @param texts the non-null visible texts to read, tokens already removed
     * @param languageTag the source language's tag, or null when it is not known (no pronoun table, no evidence)
     * @return the pronouns seen; empty evidence when the language lists no pronouns
     */
    public static Evidence of(final String term, final List<String> texts, @Nullable final String languageTag) {
        return of(term, term, texts, languageTag, (text, use) -> true);
    }

    /**
     * Counts the pronouns that follow every name of an entry: its lemma and its forms, a form that fits another name
     * too counted only where the clause decides it for this one ({@link LemmaMentions}).
     *
     * @param entry the non-null entry
     * @param texts the non-null visible texts to read, tokens already removed
     * @param languageTag the source language's tag, or null when it is not known (no pronoun table, no evidence)
     * @return the pronouns seen; empty evidence when the language lists no pronouns
     */
    public static Evidence of(final GlossaryEntry entry, final List<String> texts, @Nullable final String languageTag) {
        return LemmaMentions.evidence(entry, texts, languageTag);
    }

    /**
     * Counts the pronouns that follow one name of an entry, and the past-tense verbs that follow a mention written as
     * the entry's term: an oblique form ({@code Ключ Велемира лежал}) is no subject, so its verb is not counted.
     *
     * @param term the non-blank name whose mentions are read
     * @param lemma the entry's term; a mention takes a verb only when it is written so
     * @param texts the non-null visible texts to read
     * @param languageTag the source language's tag, or null when it is not known
     * @param keep which mentions count, given the text they stand in
     * @return the evidence; empty when the language lists no pronouns
     */
    static Evidence of(
            final String term,
            final String lemma,
            final List<String> texts,
            @Nullable final String languageTag,
            final BiPredicate<String, Mention> keep) {
        return NameEvidence.read(term, lemma, texts, languageTag, keep);
    }

    /**
     * The entry as the book's pronouns and verbs leave it. A person's entry, or a term or untyped entry, that has no
     * gender and was never tried gets the dominant gender: from the book ({@link Origin#BOOK}) when the evidence is
     * strong ({@link Evidence#isStrong()}), else as likely; a term entry becomes a character. A gender a rule or a guess
     * set is replaced only by strong evidence, which makes it the book's. Nothing a person or the book set, nothing
     * locked and no place or title is touched.
     *
     * @param entry the non-null entry
     * @param texts the non-null visible texts of the book
     * @param languageTag the source language's tag, or null when it is not known (no pronoun table, no evidence)
     * @return the entry with a gender from the evidence, or {@code entry} itself when nothing is to be set
     */
    public static GlossaryEntry seeded(
            final GlossaryEntry entry, final List<String> texts, @Nullable final String languageTag) {
        Objects.requireNonNull(entry, "entry");
        final boolean open = entry.gender() == Gender.UNKNOWN && !entry.genderSeedTried();
        final boolean guessed = entry.gender() != Gender.UNKNOWN && entry.isGenderSuggested();
        if (entry.locked() || !isPersonLike(entry.type()) || (!open && !guessed)) {
            return entry;
        }
        final Evidence evidence = of(entry, texts, languageTag);
        final Optional<Gender> dominant = TitledGender.dominant(entry.term(), texts, evidence);
        final boolean sets = dominant.isPresent() && (open || evidence.isStrong());
        final Origin origin = evidence.isStrong() ? Origin.BOOK : Origin.LIKELY;
        log.debug(
                "Gender evidence {} female, {} male, strong {}, held {}: sets {} as {}",
                evidence.female(),
                evidence.male(),
                evidence.isStrong(),
                entry.genderOrigin(),
                sets,
                origin);
        if (dominant.isEmpty() || !sets) {
            return entry;
        }
        log.trace(
                "Glossary entry {} gets the gender {} from the book's pronouns and verbs", entry.id(), dominant.get());
        final GlossaryEntry character = retypes(entry) ? entry.withType(TermType.CHARACTER, origin) : entry;
        return origin == Origin.BOOK
                ? character.withGenderFrom(dominant.get(), Origin.BOOK)
                : character.withSuggestedGender(dominant.get());
    }

    /**
     * The entries as {@link #seeded(GlossaryEntry, List, String)} would leave them, in order.
     *
     * @param entries the non-null entries
     * @param texts the non-null visible texts of the book
     * @param languageTag the source language's tag, or null when it is not known (no pronoun table, no evidence)
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
     * @param languageTag the source language's tag, or null when it is not known (no pronoun table, no evidence)
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

    // The count makes a character of the unset kind or of an automatic one, as the book's or a likely kind like the
    // gender it sets; a kind the person chose stays theirs (design D3).
    private static boolean retypes(final GlossaryEntry entry) {
        final boolean retypes = entry.type() == TermType.OTHER
                || (entry.type() != TermType.CHARACTER && entry.typeOrigin() != Origin.USER);
        log.debug(
                "Glossary entry {} kind {} from {}: made a character {}",
                entry.id(),
                entry.type(),
                entry.typeOrigin(),
                retypes);
        return retypes;
    }

    private static boolean isPersonLike(final TermType type) {
        return type == TermType.CHARACTER || type == TermType.TERM || type == TermType.OTHER;
    }
}
