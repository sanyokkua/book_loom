package ua.bookloom.pipeline.proofread;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.checks.CharacterName;
import ua.bookloom.pipeline.checks.GenderCheck;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * The gender a gender deferral waited for, once it is known: a glossary entry's gender the person or the book gave
 * (never a likely one), or the narrator's stated or inferred one. Code then holds the deferral's segment to it with the
 * target language's gender check, so a segment with no word that agrees with the gender is settled with no call.
 *
 * @param name the name the reason shows: the entry's target, else its term, or {@value #NARRATOR}
 * @param gender the known gender
 * @param narrator whether the deferral waited on the narrator rather than a character
 */
@Slf4j
record ArrivedGender(String name, Gender gender, boolean narrator) {

    /** How a narrator deferral's reason names the one it waited on. */
    static final String NARRATOR = "narrator";

    /** Rejects a missing part. */
    ArrivedGender {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(gender, "gender");
    }

    /**
     * The fact a deferral waited for, when it has arrived.
     *
     * @param deferral the non-null open deferral
     * @param byTerm the glossary entries by term
     * @param narratorNow the brief's narrator as it is now
     * @return the known gender, or empty when the deferral is no gender one or its gender is still unknown or only
     *     likely
     */
    static Optional<ArrivedGender> of(
            final Deferral deferral, final Map<String, GlossaryEntry> byTerm, final Narrator narratorNow) {
        final String waitingOn = deferral.waitingOn();
        final boolean genderReason = deferral.reason() == DeferralReason.GENDER_UNKNOWN
                || deferral.reason() == DeferralReason.GENDER_CHANGED;
        if (!genderReason || waitingOn == null) {
            return Optional.empty();
        }
        if (DeferralRegister.NARRATOR.equals(waitingOn)) {
            return narratorNow.hasCheckableGender()
                    ? Optional.of(new ArrivedGender(NARRATOR, narratorNow.gender(), true))
                    : Optional.empty();
        }
        final GlossaryEntry entry = byTerm.get(waitingOn);
        if (entry == null || !entry.hasConfirmedGender()) {
            return Optional.empty();
        }
        final String target = entry.target();
        return Optional.of(
                new ArrivedGender(target == null || target.isBlank() ? entry.term() : target, entry.gender(), false));
    }

    /**
     * Whether a decided text holds a word that disagrees with the gender.
     *
     * @param text the non-null decided target, plain
     * @param targetLanguage the non-null target language tag, whose language file names its gender check
     * @return {@code true} if the check finds a word of the other gender, {@code false} otherwise and for a language
     *     with no gender check
     */
    boolean mismatches(final String text, final String targetLanguage) {
        final GenderCheck check = GenderChecks.named(LanguageRules.bundled().genderCheck(targetLanguage));
        final boolean found = narrator
                ? !check.find(text, gender).isEmpty()
                : !check.findCharacters(text, List.of(new CharacterName(name, gender)))
                        .isEmpty();
        log.debug("Gender check of a settled fact narrator={} gender={} mismatch={}", narrator, gender, found);
        return found;
    }

    /** {@return the mark's reason, such as {@code gender now known: Івар — female}} */
    String reason() {
        return "gender now known: " + name + " — " + gender.name().toLowerCase(Locale.ROOT);
    }
}
