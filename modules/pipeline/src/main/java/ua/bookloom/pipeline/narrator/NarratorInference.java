package ua.bookloom.pipeline.narrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PastTenseReader;

/**
 * Infers an unstated first-person narrator's gender from the decided targets (15i.T5, 15f.A4): after
 * {@link #MIN_SEGMENTS} first-person segments, when at least {@link #MIN_FORMS} forms of the narrator's own «я» plus
 * a past-tense verb stand outside quotes and dialogue, and {@link #AGREEMENT} of them agree on one gender, that is the
 * narrator's gender; the segments holding the other gender's forms are the minority.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NarratorInference {

    /** How many first-person segments must be decided before the narrator's own forms are counted. */
    public static final int MIN_SEGMENTS = 20;

    /** How many «я + past verb» forms the narrator's own words must hold. */
    public static final int MIN_FORMS = 8;

    /** The share of the forms that must agree, inclusive. */
    public static final double AGREEMENT = 0.8;

    private static final long PERCENT = 100;

    /**
     * What the count found.
     *
     * @param gender the gender most forms show; male or female
     * @param forms how many forms were counted
     * @param agreeing how many of them show {@code gender}
     * @param minority the indexes, in the list given to {@link #infer}, of the segments holding a form of the other
     *     gender, in order; never null, empty when all agree
     */
    public record Verdict(Gender gender, int forms, int agreeing, List<Integer> minority) {}

    /**
     * Counts the narrator's own past-tense forms.
     *
     * @param firstPersonTargets the decided targets of the first-person segments, in book order
     * @param targetLanguage the target language tag, whose past endings and first-person pronouns read the forms
     *     ({@link PastTenseReader})
     * @return the verdict, or empty when there are fewer than {@link #MIN_SEGMENTS} segments, fewer than
     *     {@link #MIN_FORMS} forms outside quotes, or no gender holds {@link #AGREEMENT} of them, and always when the
     *     language lists no gendered past forms
     */
    public static Optional<Verdict> infer(final List<String> firstPersonTargets, final String targetLanguage) {
        Objects.requireNonNull(firstPersonTargets, "firstPersonTargets");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        log.debug(
                "Narrator inference over {} first-person segments language={}",
                firstPersonTargets.size(),
                targetLanguage);
        if (firstPersonTargets.size() < MIN_SEGMENTS) {
            log.debug("Narrator inference waits: fewer than {} first-person segments", MIN_SEGMENTS);
            return Optional.empty();
        }
        final PastTenseReader reader = PastTenseReader.of(targetLanguage);
        if (reader == null) {
            log.debug("Narrator inference skipped: {} lists no gendered past forms", targetLanguage);
            return Optional.empty();
        }
        return decided(firstPersonTargets, reader);
    }

    private static Optional<Verdict> decided(final List<String> targets, final PastTenseReader reader) {
        final Tally feminine = new Tally();
        final Tally masculine = new Tally();
        for (int index = 0; index < targets.size(); index++) {
            final PastTenseReader.FirstPersonCount count = reader.firstPerson(List.of(targets.get(index)));
            feminine.add(index, count.female());
            masculine.add(index, count.male());
        }
        log.debug("Narrator inference counted {} feminine and {} masculine forms", feminine.forms, masculine.forms);
        final boolean isFemale = feminine.forms > masculine.forms;
        final Tally winner = isFemale ? feminine : masculine;
        final Tally loser = isFemale ? masculine : feminine;
        final int forms = winner.forms + loser.forms;
        if (forms < MIN_FORMS || winner.forms * PERCENT < Math.round(AGREEMENT * PERCENT) * forms) {
            log.debug("Narrator inference decides nothing: {} of {} forms agree", winner.forms, forms);
            return Optional.empty();
        }
        return Optional.of(
                new Verdict(isFemale ? Gender.FEMALE : Gender.MALE, forms, winner.forms, List.copyOf(loser.segments)));
    }

    // One gender's forms: how many, and the segments that hold one, each once and in order.
    private static final class Tally {
        private final List<Integer> segments = new ArrayList<>();
        private int forms;

        void add(final int segment, final int found) {
            forms += found;
            if (found > 0) {
                segments.add(segment);
            }
        }
    }
}
