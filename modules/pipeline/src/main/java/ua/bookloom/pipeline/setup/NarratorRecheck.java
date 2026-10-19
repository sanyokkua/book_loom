package ua.bookloom.pipeline.setup;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;

/**
 * Checks the quotes a first-person narrator of unknown gender kept as pending evidence against the glossary as it
 * stands now (15i.T5): a quote that calls the narrator by a name that is now a glossary character of confirmed gender
 * (the person's or verified, never a suggestion) makes the narrator that gender. The same filter as when the brief was
 * suggested holds: a name the narrator says to someone else ("Mind the step, Lucy," I said) names that person, not the
 * narrator. No model is asked and nothing is written.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NarratorRecheck {

    private static final Pattern NAME_WORD = Pattern.compile("\\p{Lu}[\\p{L}\\p{M}'’-]*");

    /**
     * The narrator after the check.
     *
     * @param narrator the brief's narrator
     * @param glossary the book's glossary entries as they stand
     * @param book the book's paragraphs as one sample, which the quotes are found in
     * @param languageTag the source language's tag, or null when it is not known
     * @return the narrator itself unless it is a first-person narrator of unknown gender with a pending quote that
     *     names a glossary character of confirmed gender, else the same narrator verified with the proving quotes
     */
    static Narrator of(
            final Narrator narrator,
            final List<GlossaryEntry> glossary,
            final BookSample book,
            final @Nullable String languageTag) {
        Objects.requireNonNull(narrator, "narrator");
        if (!hasPendingQuotes(narrator)) {
            log.debug("Narrator re-check: nothing pending for {} {}", narrator.person(), narrator.gender());
            return narrator;
        }
        final NarratorGenderEvidence evidence = new NarratorGenderEvidence(
                glossary.stream().filter(GlossaryEntry::hasConfirmedGender).toList(), languageTag);
        final Map<String, Gender> proofs = new LinkedHashMap<>();
        narrator.evidence()
                .forEach(quote -> provedBy(quote, evidence, book).ifPresent(gender -> proofs.put(quote, gender)));
        final Set<Gender> genders = Set.copyOf(proofs.values());
        if (genders.size() != 1) {
            log.debug("Narrator re-check: the pending quotes prove {} genders", genders.size());
            return narrator;
        }
        final Gender gender = genders.iterator().next();
        log.info("Narrator re-check: {} proved by {} pending quotes", gender, proofs.size());
        return new Narrator(narrator.person(), gender, Narrator.GenderOrigin.VERIFIED, List.copyOf(proofs.keySet()));
    }

    private static boolean hasPendingQuotes(final Narrator narrator) {
        return narrator.person() == NarratorPerson.FIRST
                && narrator.gender() == Gender.UNKNOWN
                && !narrator.evidence().isEmpty();
    }

    // The one gender the names in the quote prove, or none when none does, two disagree, or the quote is not in the
    // book.
    private static Optional<Gender> provedBy(
            final String quote, final NarratorGenderEvidence evidence, final BookSample book) {
        if (!book.holds(quote)) {
            log.warn("Narrator re-check: a pending quote is not in the book any more");
            return Optional.empty();
        }
        final Set<Gender> genders = EnumSet.noneOf(Gender.class);
        final Matcher names = NAME_WORD.matcher(quote);
        while (names.find()) {
            final String name = names.group();
            if (evidence.isSaidToAnother(book, quote, name)) {
                log.debug("Narrator re-check: the narrator says '{}' to someone else", name);
                continue;
            }
            evidence.glossaryGenderOf(quote, name).ifPresent(genders::add);
        }
        log.trace("Narrator re-check quote '{}' proves {}", quote, genders);
        return genders.size() == 1 ? Optional.of(genders.iterator().next()) : Optional.empty();
    }
}
