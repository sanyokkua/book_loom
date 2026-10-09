package ua.bookloom.ui.state;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;

/**
 * The model's proposal of the Book Brief's tone and style: asks for it, and writes the answer into the brief through the
 * view model's own change path, so it is saved and observed like a person's edit. A narrator the model could not tell
 * leaves the person's own choice alone. The evidence behind each field (the book's words the model quoted, and how many
 * of the samples agreed) is kept for the screen to show while the field still holds the value the suggestion wrote.
 * FX thread only.
 */
@Slf4j
public final class StyleSuggestion {

    private final CurrentProject project;
    private final @Nullable SetupHelper setup;
    private final BiConsumer<String, UnaryOperator<BookBrief>> change;
    private final ReadOnlyBooleanWrapper suggesting = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<StyleSuggestionOutcome> outcome =
            new ReadOnlyObjectWrapper<>(StyleSuggestionOutcome.NONE);
    private final ReadOnlyObjectWrapper<Map<BriefField, FieldEvidence>> evidence =
            new ReadOnlyObjectWrapper<>(Map.of());
    // The brief right after the suggestion was written, so a field the person has changed since shows no evidence.
    private @Nullable BookBrief applied;

    StyleSuggestion(
            final CurrentProject project,
            final @Nullable SetupHelper setup,
            final BiConsumer<String, UnaryOperator<BookBrief>> change) {
        this.project = Objects.requireNonNull(project, "project");
        this.setup = setup;
        this.change = Objects.requireNonNull(change, "change");
        project.book().addListener((observed, was, now) -> forget());
    }

    /** Whether a model-suggested style is offered at all: a setup helper exists. */
    public boolean isOffered() {
        return setup != null;
    }

    /** Whether a suggestion is waiting for the model. */
    public ReadOnlyBooleanProperty suggesting() {
        return suggesting.getReadOnlyProperty();
    }

    /** What became of the last suggestion; {@link StyleSuggestionOutcome#NONE} while there is nothing to say. */
    public ReadOnlyObjectProperty<StyleSuggestionOutcome> outcome() {
        return outcome.getReadOnlyProperty();
    }

    /** The evidence of the last suggestion's fields; empty before any and after the book changed. */
    public ReadOnlyObjectProperty<Map<BriefField, FieldEvidence>> evidence() {
        return evidence.getReadOnlyProperty();
    }

    /**
     * The evidence to show beside one field of the brief.
     *
     * @param field the field
     * @param current the brief as it is now
     * @return the field's evidence if the last suggestion wrote it and the field still holds what it wrote, or empty
     */
    public Optional<FieldEvidence> evidenceFor(final BriefField field, final BookBrief current) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(current, "current");
        final BookBrief written = applied;
        if (written == null || !Objects.equals(valueOf(field, written), valueOf(field, current))) {
            return Optional.empty();
        }
        return Optional.ofNullable(evidence.get().get(field));
    }

    private static @Nullable Object valueOf(final BriefField field, final BookBrief brief) {
        return switch (field) {
            case GENRE -> brief.genre();
            case REGISTER -> brief.register();
            case VOICE -> brief.voiceEra();
            case AUDIENCE -> brief.audience();
            case NARRATOR -> brief.narrator().person();
            case NARRATOR_GENDER -> brief.narrator().gender();
        };
    }

    private void forget() {
        log.debug("the style suggestion's evidence is forgotten: the open book changed");
        applied = null;
        evidence.set(Map.of());
    }

    /** Asks the model, and writes its answer into the brief of the book that is open when it arrives. */
    public void ask() {
        final OpenedBook book = project.book().get();
        if (setup == null || book == null || suggesting.get()) {
            return;
        }
        log.debug("a style suggestion is asked for project {}", book.projectId());
        outcome.set(StyleSuggestionOutcome.NONE);
        forget();
        suggesting.set(true);
        setup.runShown(
                        ActivityKind.SUGGEST_STYLE,
                        (model, progress) -> setup.assistant().suggestBrief(book.projectId(), model, progress),
                        answer -> answered(book.projectId(), answer))
                .ifPresent(refusal -> {
                    suggesting.set(false);
                    outcome.set(StyleSuggestionOutcome.of(
                            refusal == SetupHelper.Refusal.NO_MODEL
                                    ? StyleSuggestionOutcome.Kind.NO_MODEL
                                    : StyleSuggestionOutcome.Kind.BUSY));
                });
    }

    private void answered(final String projectId, final Result<BriefSuggestion> answer) {
        suggesting.set(false);
        final OpenedBook now = project.book().get();
        if (now == null || !now.projectId().equals(projectId)) {
            log.debug("the style suggestion is dropped: the open book changed");
            return;
        }
        final AppError failure = answer.error();
        if (failure != null && failure.code() == ErrorCode.cancelled) {
            log.info("the style suggestion was stopped by the person");
            return;
        }
        if (failure != null) {
            log.warn("the style suggestion failed with {}", failure.code());
            outcome.set(new StyleSuggestionOutcome(StyleSuggestionOutcome.Kind.FAILED, failure.message()));
            return;
        }
        final BriefSuggestion suggestion = Objects.requireNonNull(answer.data(), "data");
        final BookBrief before = project.brief().get();
        final BriefSuggestion aligned = before == null ? suggestion : suggestion.alignedTo(before.narrator());
        change.accept("style suggestion", brief -> withSuggestion(brief, suggestion));
        applied = project.brief().get();
        log.debug(
                "the style suggestion wrote the brief; evidence for {}",
                aligned.evidence().keySet());
        evidence.set(aligned.evidence());
        outcome.set(StyleSuggestionOutcome.of(StyleSuggestionOutcome.Kind.DONE));
    }

    // The fields the model filled replace the brief's; one it left empty keeps what the person wrote, and the policies,
    // dial and languages are not its business.
    private static BookBrief withSuggestion(final BookBrief brief, final BriefSuggestion guess) {
        final BriefSuggestion suggestion = guess.alignedTo(brief.narrator());
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                suggestion.genre() == null ? brief.genre() : knownGenre(suggestion.genre()),
                suggestion.register(),
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

    /** The genre as the list names it when the model named one of the predefined genres, else as free text. */
    static String knownGenre(final String genre) {
        return Arrays.stream(Genre.values())
                .map(Genre::english)
                .filter(english -> english.equalsIgnoreCase(genre.strip()))
                .findFirst()
                .orElse(genre.strip());
    }

    /**
     * The narrator the suggestion states, but only while the brief names none: a narrator the person chose, or answered
     * at Start, is never replaced by a guess. The brief's own when the model could not tell.
     */
    static Narrator narratorFor(final BookBrief brief, final BriefSuggestion suggestion) {
        return suggestion.narrator() == NarratorPerson.UNSPECIFIED
                        || brief.narrator().person() != NarratorPerson.UNSPECIFIED
                ? brief.narrator()
                : new Narrator(suggestion.narrator(), suggestion.narratorGender());
    }
}
