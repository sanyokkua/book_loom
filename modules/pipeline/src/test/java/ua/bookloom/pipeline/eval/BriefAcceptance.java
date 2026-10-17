package ua.bookloom.pipeline.eval;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Genre;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * What a person who accepts the suggested Book Brief ends up with. The window writes the suggestion in
 * {@code StyleSuggestion} (in {@code :ui}, which the pipeline tests cannot reach), so this mirrors its rules: a field
 * the model left empty keeps the brief's, an uncertain register stays the brief's, the policies, dial and languages
 * are never the model's business, and a narrator comes from {@link BriefSuggestion#alignedTo}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefAcceptance {

    static BookBrief accept(final BookBrief brief, final BriefSuggestion guess) {
        Objects.requireNonNull(brief, "brief");
        final BriefSuggestion suggestion =
                Objects.requireNonNull(guess, "guess").alignedTo(brief.narrator());
        log.debug(
                "Brief accepted register={} narrator={} gender={}",
                suggestion.register(),
                suggestion.narrator(),
                suggestion.narratorGender());
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                suggestion.genre() == null ? brief.genre() : known(suggestion.genre()),
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

    private static String known(final String genre) {
        return Genre.ofEnglish(genre).map(Genre::english).orElse(genre.strip());
    }

    private static Register registerFor(final BookBrief brief, final BriefSuggestion suggestion) {
        return suggestion
                        .evidenceOf(BriefField.REGISTER)
                        .filter(proof -> !proof.isKept())
                        .isPresent()
                ? brief.register()
                : suggestion.register();
    }

    private static Narrator narratorFor(final BookBrief brief, final BriefSuggestion aligned) {
        if (aligned.narrator() == NarratorPerson.UNSPECIFIED) {
            return brief.narrator();
        }
        final Narrator held = brief.narrator();
        final Narrator suggested = aligned.suggestedNarrator();
        final boolean newPending = held.gender() == Gender.UNKNOWN
                && !suggested.evidence().isEmpty()
                && !suggested.evidence().equals(held.evidence());
        final boolean unchanged =
                suggested.person() == held.person() && suggested.gender() == held.gender() && !newPending;
        return unchanged ? held : suggested;
    }
}
