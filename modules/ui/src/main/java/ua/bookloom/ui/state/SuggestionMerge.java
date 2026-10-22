package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Genre;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/** How a held suggestion is written into the brief as it stands when the person applies it. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionMerge {

    // The fields the model filled replace the brief's; one it left empty keeps what the person wrote, and the policies,
    // dial and languages are not its business.
    static BookBrief withSuggestion(final BookBrief brief, final BriefSuggestion guess) {
        final BriefSuggestion suggestion = guess.alignedTo(brief.narrator());
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                suggestion.genre() == null ? brief.genre() : knownGenre(suggestion.genre()),
                registerFor(brief, suggestion),
                suggestion.voiceEra() == null ? brief.voiceEra() : suggestion.voiceEra(),
                suggestion.audience() == null ? brief.audience() : suggestion.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial(),
                narratorFor(brief, suggestion));
    }

    // An uncertain register is not a suggestion: the person's own stays, as the field's tip says.
    private static Register registerFor(final BookBrief brief, final BriefSuggestion suggestion) {
        return suggestion
                        .evidenceOf(BriefField.REGISTER)
                        .filter(proof -> !proof.isKept())
                        .isPresent()
                ? brief.register()
                : suggestion.register();
    }

    // The genre as the list names it when the model named one of the predefined genres, else as free text. */
    static String knownGenre(final String genre) {
        return Genre.ofEnglish(genre).map(Genre::english).orElse(genre.strip());
    }

    /**
     * The narrator the suggestion states once it is held to the brief's own ({@link BriefSuggestion#alignedTo}): a
     * narrator the person chose, or answered at Start, is never replaced by a guess, but a first-person narrator with no
     * gender takes the gender the suggestion proved, with the quotes it was verified from and the origin
     * {@link Narrator.GenderOrigin#VERIFIED}; one with no gender takes the pending quotes the suggestion kept
     * (15i.T5). The brief's own when the model could not tell or said the same.
     */
    static Narrator narratorFor(final BookBrief brief, final BriefSuggestion aligned) {
        if (aligned.narrator() == NarratorPerson.UNSPECIFIED) {
            return brief.narrator();
        }
        final Narrator suggested = aligned.suggestedNarrator();
        final boolean unchanged = suggested.person() == brief.narrator().person()
                && suggested.gender() == brief.narrator().gender()
                && !holdsNewPendingQuotes(brief.narrator(), suggested);
        return unchanged ? brief.narrator() : suggested;
    }

    // A narrator of unknown gender takes the quotes the suggestion could not verify yet; Start checks them again.
    private static boolean holdsNewPendingQuotes(final Narrator held, final Narrator suggested) {
        return held.gender() == Gender.UNKNOWN
                && !suggested.evidence().isEmpty()
                && !suggested.evidence().equals(held.evidence());
    }
}
