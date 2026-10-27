package ua.bookloom.ui.state;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
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
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The model's proposal of the Book Brief's tone and style: asks for it, holds the answer until the person applies it,
 * and then writes it into the brief through the view model's own change path, so it is saved and observed like a
 * person's edit. A register the samples did not agree on and a narrator the model could not tell leave the person's own
 * choice alone; a narrator gender it proved fills only an unknown one, and is shown as suggested. The evidence behind
 * each field (the book's words the model quoted, and how many of the samples agreed) and the language proposal are kept
 * only on Apply, and the evidence shows while the field still holds the value the suggestion wrote. FX thread only.
 */
@Slf4j
public final class StyleSuggestion {

    private final CurrentProject project;
    private final @Nullable SetupHelper setup;
    private final BiConsumer<String, UnaryOperator<BookBrief>> change;
    private final ReadOnlyBooleanWrapper suggesting = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<@Nullable PendingSuggestion> pending = new ReadOnlyObjectWrapper<>(null);
    private final ReadOnlyObjectWrapper<StyleSuggestionOutcome> outcome =
            new ReadOnlyObjectWrapper<>(StyleSuggestionOutcome.NONE);
    private final ReadOnlyObjectWrapper<Map<BriefField, FieldEvidence>> evidence =
            new ReadOnlyObjectWrapper<>(Map.of());
    // Held only while the brief's source was Auto when the answer came; the person takes it with "Use it" or never.
    private final ReadOnlyObjectWrapper<@Nullable String> languageProposal = new ReadOnlyObjectWrapper<>(null);
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

    /**
     * The answer held for the person, which writes nothing until {@link #apply()}.
     *
     * @return a read-only property holding {@code null} while there is none
     */
    public ReadOnlyObjectProperty<@Nullable PendingSuggestion> pending() {
        return pending.getReadOnlyProperty();
    }

    /** The evidence of the last suggestion's fields; empty before any and after the book changed. */
    public ReadOnlyObjectProperty<Map<BriefField, FieldEvidence>> evidence() {
        return evidence.getReadOnlyProperty();
    }

    /**
     * The language the last suggestion found the passages written in, offered while the brief's source is Auto. It is
     * a proposal the person takes with "Use it"; it is never written into the brief.
     *
     * @return a read-only property holding a language tag, or {@code null} when there is none to offer
     */
    public ReadOnlyObjectProperty<@Nullable String> languageProposal() {
        return languageProposal.getReadOnlyProperty();
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

    /**
     * Whether the narrator's gender shown is the model's suggestion, not the person's choice, so the screen can mark it.
     *
     * @param current the brief as it is now
     * @return {@code true} if the last suggestion wrote a male or female narrator gender, backed by evidence, and the
     *     brief still holds it; {@code false} otherwise
     */
    public boolean isNarratorGenderSuggested(final BookBrief current) {
        final Gender gender =
                Objects.requireNonNull(current, "current").narrator().gender();
        return (gender == Gender.MALE || gender == Gender.FEMALE)
                && evidenceFor(BriefField.NARRATOR_GENDER, current)
                        .filter(FieldEvidence::isKept)
                        .isPresent();
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
        dropHeld();
        applied = null;
        evidence.set(Map.of());
        languageProposal.set(null);
    }

    /** Asks the model, and holds its answer for the book that is open when it arrives until it is applied. */
    public void ask() {
        final OpenedBook book = project.book().get();
        if (setup == null || book == null || suggesting.get()) {
            return;
        }
        log.debug("a style suggestion is asked for project {}", book.projectId());
        outcome.set(StyleSuggestionOutcome.NONE);
        forget();
        suggesting.set(true);
        final Supplier<Optional<Integer>> detection = setup.detection();
        setup.runForDecision(
                        ActivityKind.SUGGEST_STYLE,
                        (model, progress) -> setup.assistant()
                                .suggestBrief(
                                        book.projectId(), model, detection.get().orElse(null), progress),
                        answer -> answered(book.projectId(), answer),
                        this::decisionsOf)
                .ifPresent(this::refused);
    }

    private List<DecisionRow> decisionsOf(final Result<BriefSuggestion> answer, final List<BriefSampleRead> reads) {
        final BriefSuggestion suggestion = answer.data();
        final BookBrief now = project.brief().get();
        if (suggestion == null) {
            return List.of();
        }
        return BriefDecisions.answered(now == null ? suggestion : suggestion.alignedTo(now.narrator()), reads);
    }

    private void refused(final SetupHelper.Refusal refusal) {
        log.debug("the style suggestion was refused: {}", refusal);
        suggesting.set(false);
        switch (refusal) {
            case NO_MODEL -> outcome.set(StyleSuggestionOutcome.of(StyleSuggestionOutcome.Kind.NO_MODEL));
            case BUSY -> outcome.set(StyleSuggestionOutcome.of(StyleSuggestionOutcome.Kind.BUSY));
            case HIDDEN_WORK -> log.debug("the refusal was said by the work in the background");
        }
    }

    private Optional<ResultOffer> answered(final String projectId, final Result<BriefSuggestion> answer) {
        suggesting.set(false);
        final OpenedBook now = project.book().get();
        if (now == null || !now.projectId().equals(projectId)) {
            log.debug("the style suggestion is dropped: the open book changed");
            return Optional.empty();
        }
        final AppError failure = answer.error();
        if (failure != null && failure.code() == ErrorCode.cancelled) {
            log.info("the style suggestion was stopped by the person");
            outcome.set(StyleSuggestionOutcome.of(StyleSuggestionOutcome.Kind.STOPPED));
            return Optional.empty();
        }
        if (failure != null) {
            log.warn("the style suggestion failed with {}", failure.code());
            outcome.set(new StyleSuggestionOutcome(StyleSuggestionOutcome.Kind.FAILED, failure));
            return Optional.empty();
        }
        final BriefSuggestion suggestion = Objects.requireNonNull(answer.data(), "data");
        pending.set(new PendingSuggestion(
                suggestion, Objects.requireNonNull(project.brief().get(), "brief")));
        log.info("the style suggestion is held for the person to apply");
        return Optional.of(new ResultOffer(MessageKey.BRIEF_APPLY, true, this::applyHeld, this::discardHeld));
    }

    /**
     * Writes the held answer into the brief as it is now, as the dialog's Apply does; ignored when none is held.
     */
    public void apply() {
        if (pending.get() == null) {
            return;
        }
        heldOffer().map(ResultOffer::apply).ifPresentOrElse(Runnable::run, this::applyHeld);
    }

    /** Drops the held answer and writes nothing, as Cancel and Esc do; ignored when none is held. */
    public void discard() {
        if (pending.get() == null) {
            return;
        }
        heldOffer().map(ResultOffer::discard).ifPresentOrElse(Runnable::run, this::discardHeld);
    }

    private Optional<ResultOffer> heldOffer() {
        return setup == null ? Optional.empty() : setup.heldOffer(ActivityKind.SUGGEST_STYLE);
    }

    private void dropHeld() {
        if (pending.get() != null) {
            log.debug("a held suggestion is dropped");
            discard();
        }
    }

    private void applyHeld() {
        final PendingSuggestion held = pending.get();
        if (held == null) {
            return;
        }
        pending.set(null);
        final BriefSuggestion suggestion = held.suggestion();
        final BookBrief before = project.brief().get();
        final BriefSuggestion aligned = before == null ? suggestion : suggestion.alignedTo(before.narrator());
        change.accept("style suggestion", brief -> SuggestionMerge.withSuggestion(brief, suggestion));
        applied = project.brief().get();
        log.info(
                "the style suggestion was applied; evidence for {}",
                aligned.evidence().keySet());
        evidence.set(aligned.evidence());
        holdLanguageProposal(suggestion, before);
        outcome.set(StyleSuggestionOutcome.of(StyleSuggestionOutcome.Kind.DONE));
    }

    private void discardHeld() {
        if (pending.get() != null) {
            pending.set(null);
            log.info("the style suggestion was discarded; the brief is unchanged");
            outcome.set(StyleSuggestionOutcome.NONE);
        }
    }

    /**
     * Stores a narrator the code settled, such as the one the Start re-check verified (15i.T5), with its origin and
     * quotes; the person's own choices go through the brief's narrator setters.
     *
     * @param narrator the narrator to hold
     */
    public void adoptNarrator(final Narrator narrator) {
        Objects.requireNonNull(narrator, "narrator");
        change.accept("narrator re-check", brief -> brief.withNarrator(narrator));
    }

    // A source the person or the file named is never second-guessed: the proposal is held for an Auto source only.
    private void holdLanguageProposal(final BriefSuggestion suggestion, final @Nullable BookBrief before) {
        final boolean auto = before != null && before.sourceLanguage() == null;
        final String proposed = auto ? suggestion.languageProposal() : null;
        log.debug("the language proposal is {}: source is Auto {}", proposed == null ? "not held" : "held", auto);
        log.trace("the language proposal is '{}'", proposed);
        languageProposal.set(proposed);
    }
}
