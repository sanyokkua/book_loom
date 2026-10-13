package ua.bookloom.pipeline.heal;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.checks.CharacterName;
import ua.bookloom.pipeline.checks.CharacterNames;
import ua.bookloom.pipeline.checks.GenderCheck;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.reviewer.GenderGuess;

/**
 * Reads the genders a chunk only guesses — the sheet's {@code likely} characters and a narrator gender shown as
 * suggested — into the {@link GenderGuess} the reviewer's edits are held to. The language's own gender check is the
 * measure: an edit that clears findings the check would raise if the guess were a fact is an edit toward the guess.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GuessedGenders {

    static GenderGuess of(final LoopSettings settings) {
        final List<CharacterName> likely = CharacterNames.likelyOf(settings.characters(), settings.glossaryPairs());
        final Narrator narrator = settings.frame().narrator();
        final boolean narratorGuessed = isGuessed(narrator);
        log.debug("Guessed genders likelyCharacters={} narratorGuessed={}", likely.size(), narratorGuessed);
        if (likely.isEmpty() && !narratorGuessed) {
            return GenderGuess.NONE;
        }
        final GenderCheck check = GenderChecks.named(
                LanguageRules.bundled().genderCheck(settings.frame().targetLanguage()));
        return (before, after) -> {
            final boolean favoured = fewerFindings(
                            check.findCharacters(before, likely), check.findCharacters(after, likely))
                    || (narratorGuessed
                            && fewerFindings(
                                    check.find(before, narrator.gender()), check.find(after, narrator.gender())));
            log.debug("Edit favours a guessed gender: {}", favoured);
            return favoured;
        };
    }

    private static boolean isGuessed(final Narrator narrator) {
        return narrator.person() == NarratorPerson.FIRST
                && (narrator.gender() == Gender.MALE || narrator.gender() == Gender.FEMALE)
                && narrator.genderOrigin() == Narrator.GenderOrigin.SUGGESTED;
    }

    private static boolean fewerFindings(final List<?> before, final List<?> after) {
        return after.size() < before.size();
    }
}
