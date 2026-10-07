package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The choices a person makes about the open book before a run: the languages, the tone, the four policies, the
 * balance, the auxiliary text that is also translated and the quality dial.
 *
 * <p>The brief itself lives in {@link CurrentProject}, so that a run started from any screen reads it as it stands and
 * a trip to another screen keeps it; this class changes it and saves it. The source language is preselected by the
 * project service when the book is imported, and is never derived again here.
 *
 * <p>Every change replaces the brief in {@link CurrentProject} at once and is saved through
 * {@link ProjectService#updateBrief} on the background executor. Saves are sent one at a time and in order: a change
 * made while one is in flight waits, and only the newest waiting brief is sent, because the pool would otherwise let an
 * older brief overtake a newer one and leave the project holding it. Everything here is touched on the FX thread only.
 */
@Slf4j
@Singleton
public final class BookBriefViewModel {

    private static final int MIN_BALANCE = 0;
    private static final int MAX_BALANCE = 100;

    private final CurrentProject project;
    private final BriefSaver saver;
    private final ReadOnlyBooleanWrapper sourceUndeclared = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper sameLanguage = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper targetUntested = new ReadOnlyBooleanWrapper(false);
    private final LanguageSupport languages;
    private final ReadOnlyBooleanWrapper canContinue = new ReadOnlyBooleanWrapper(false);
    private final NarratorNotice narratorNotice;
    private final StyleSuggestion style;

    /**
     * Follows the open book's brief.
     *
     * @param project the holder of the open book and its brief, which outlives this view model
     * @param projects the port a changed brief is saved through
     * @param executor the daemon executor a save runs on, never the FX thread
     * @param languages tells whether the chosen target language has tested translation rules
     */
    @Inject
    public BookBriefViewModel(
            final CurrentProject project,
            final ProjectService projects,
            @BackgroundExecutor final ExecutorService executor,
            final LanguageSupport languages,
            final @Nullable SetupHelper setup) {
        this.style = new StyleSuggestion(project, setup, (what, op) -> change(what, "applied", op));
        this.project = Objects.requireNonNull(project, "project");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.saver = new BriefSaver(
                Objects.requireNonNull(projects, "projects"), Objects.requireNonNull(executor, "executor"));
        project.brief().addListener((observed, was, now) -> derive(now));
        derive(project.brief().get());
        this.narratorNotice = new NarratorNotice(project, this::setNarratorPerson);
        log.debug(
                "book brief created, a book is already open: {}", project.book().get() != null);
    }

    /** A view model that offers no model-suggested style, for a window with no setup helper. */
    public BookBriefViewModel(
            final CurrentProject project,
            final ProjectService projects,
            final ExecutorService executor,
            final LanguageSupport languages) {
        this(project, projects, executor, languages, null);
    }

    /**
     * The brief as it stands.
     *
     * @return a read-only property holding {@code null} while no book is open
     */
    public ReadOnlyObjectProperty<@Nullable BookBrief> brief() {
        return project.brief();
    }

    /**
     * Whether a book is open whose brief has no source language: the book declares none the application recognises
     * and none has been chosen.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty sourceUndeclared() {
        return sourceUndeclared.getReadOnlyProperty();
    }

    /**
     * Whether the source and the target language are the same language.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty sameLanguage() {
        return sameLanguage.getReadOnlyProperty();
    }

    /**
     * Whether the chosen target has no tested translation rules, so the general ones are used.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty targetUntested() {
        return targetUntested.getReadOnlyProperty();
    }

    /**
     * Whether the brief can go on to the next step: both languages are chosen and they differ.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty canContinue() {
        return canContinue.getReadOnlyProperty();
    }

    /**
     * Whether the source was found to be narrated in the first person while the brief does not say the narrator's
     * gender, so the Book Brief offers a notice. It is a suggestion only: the person's choice is never overridden.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty narratorNotice() {
        return narratorNotice.shown();
    }

    /**
     * Whether a changed brief is still on its way to the project, so that a caller which needs the stored brief can
     * wait for it.
     *
     * @return a read-only property; true from a change until its save, and any save that waited behind it, finished
     */
    public ReadOnlyBooleanProperty saving() {
        return saver.saving();
    }

    /**
     * Chooses the source language.
     *
     * @param tag a normalized language tag
     */
    public void setSourceLanguage(final String tag) {
        Objects.requireNonNull(tag, "tag");
        change("source language", tag, brief -> brief.withLanguages(tag, brief.targetLanguage()));
    }

    /**
     * Chooses the target language.
     *
     * @param tag a normalized language tag
     */
    public void setTargetLanguage(final String tag) {
        Objects.requireNonNull(tag, "tag");
        change("target language", tag, brief -> brief.withLanguages(brief.sourceLanguage(), tag));
    }

    /**
     * Sets the genre the prompt names.
     *
     * @param genre the English name of a predefined genre, or free text as typed; blank clears it
     */
    public void setGenre(final String genre) {
        final String text = blankToNull(genre);
        log.trace("genre is now '{}'", text);
        change("genre", text == null ? "cleared" : "set", brief -> brief.withGenre(text));
    }

    /**
     * Sets the narrative voice and era notes.
     *
     * @param voiceEra free text; blank clears it
     */
    public void setVoiceEra(final String voiceEra) {
        final String text = blankToNull(voiceEra);
        log.trace("voice and era are now '{}'", text);
        change("voice and era", text == null ? "cleared" : "set", brief -> brief.withVoiceEra(text));
    }

    /**
     * Sets the audience.
     *
     * @param audience free text; blank clears it
     */
    public void setAudience(final String audience) {
        final String text = blankToNull(audience);
        log.trace("audience is now '{}'", text);
        change("audience", text == null ? "cleared" : "set", brief -> brief.withAudience(text));
    }

    /**
     * Sets the register.
     *
     * @param register the register the translation is written in
     */
    public void setRegister(final Register register) {
        Objects.requireNonNull(register, "register");
        change(
                "register",
                register.name(),
                brief -> BriefRebuild.of(brief, register, null, null, null, null, null, null));
    }

    /**
     * Sets how character and place names are rendered.
     *
     * @param names the name policy
     */
    public void setNames(final NamePolicy names) {
        Objects.requireNonNull(names, "names");
        change("name policy", names.name(), brief -> BriefRebuild.of(brief, null, names, null, null, null, null, null));
    }

    /**
     * Sets how foreign-language passages are handled.
     *
     * @param foreign the foreign-passage policy
     */
    public void setForeignPassages(final ForeignPassagePolicy foreign) {
        Objects.requireNonNull(foreign, "foreign");
        change(
                "foreign-passage policy",
                foreign.name(),
                brief -> BriefRebuild.of(brief, null, null, foreign, null, null, null, null));
    }

    /**
     * Sets how footnotes are handled.
     *
     * @param footnotes the footnote policy
     */
    public void setFootnotes(final FootnotePolicy footnotes) {
        Objects.requireNonNull(footnotes, "footnotes");
        change(
                "footnote policy",
                footnotes.name(),
                brief -> BriefRebuild.of(brief, null, null, null, footnotes, null, null, null));
    }

    /**
     * Sets how units of measurement are handled.
     *
     * @param units the unit policy
     */
    public void setUnits(final UnitPolicy units) {
        Objects.requireNonNull(units, "units");
        change("unit policy", units.name(), brief -> BriefRebuild.of(brief, null, null, null, null, units, null, null));
    }

    /**
     * Sets the faithful-to-natural balance.
     *
     * @param balance the balance; a value outside 0 to 100 is brought to the nearest end
     */
    public void setBalance(final int balance) {
        final int clamped = Math.max(MIN_BALANCE, Math.min(MAX_BALANCE, balance));
        change(
                "balance",
                Integer.toString(clamped),
                brief -> BriefRebuild.of(brief, null, null, null, null, null, clamped, null));
    }

    /**
     * Sets which auxiliary text is also translated.
     *
     * @param alsoTranslate the four switches
     */
    public void setAlsoTranslate(final AlsoTranslate alsoTranslate) {
        Objects.requireNonNull(alsoTranslate, "alsoTranslate");
        change(
                "also translate",
                alsoTranslate.toString(),
                brief -> BriefRebuild.of(brief, null, null, null, null, null, null, alsoTranslate));
    }

    /**
     * Sets who narrates the book; the gender is kept when only the person changes.
     *
     * @param person the narrator's grammatical person
     */
    public void setNarratorPerson(final NarratorPerson person) {
        Objects.requireNonNull(person, "person");
        change(
                "narrator person",
                person.name(),
                brief ->
                        brief.withNarrator(new Narrator(person, brief.narrator().gender())));
    }

    /**
     * Sets the narrator's gender, which a first-person narrator's verbs must agree with; the person is kept.
     *
     * @param gender the narrator's gender, or {@link Gender#UNKNOWN} when not stated
     */
    public void setNarratorGender(final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        change(
                "narrator gender",
                gender.name(),
                brief -> brief.withNarrator(new Narrator(brief.narrator().person(), gender)));
    }

    /**
     * Sets a first-person narrator of the given gender in one change, as the Start translation question does.
     *
     * @param gender the narrator's gender
     */
    public void setFirstPersonNarrator(final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        change(
                "first-person narrator",
                gender.name(),
                brief -> brief.withNarrator(new Narrator(NarratorPerson.FIRST, gender)));
    }

    /**
     * Sets the quality dial.
     *
     * @param dial the speed and quality choice
     */
    public void setDial(final QualityDial dial) {
        Objects.requireNonNull(dial, "dial");
        change("quality dial", dial.name(), brief -> brief.withDial(dial));
    }

    private static @Nullable String blankToNull(final String text) {
        Objects.requireNonNull(text, "text");
        return text.isBlank() ? null : text;
    }

    private void change(final String field, final String value, final UnaryOperator<BookBrief> edit) {
        final OpenedBook book = project.book().get();
        final BookBrief current = project.brief().get();
        if (book == null || current == null) {
            log.debug("{} not changed: no book is open", field);
            return;
        }
        final BookBrief changed = edit.apply(current);
        log.debug("{} is now {}", field, value);
        if (changed.equals(current)) {
            log.debug("{} was already so; nothing is saved", field);
            return;
        }
        project.replaceBrief(changed);
        saver.enqueue(book.projectId(), changed);
    }

    /**
     * The model's proposal of the tone and style fields.
     *
     * @return the proposal, whose {@code ask()} fills the fields from the opening of the book
     */
    public StyleSuggestion styleSuggestion() {
        return style;
    }

    private void derive(final @Nullable BookBrief brief) {
        final String source = brief == null ? null : brief.sourceLanguage();
        final String target = brief == null ? null : brief.targetLanguage();
        final boolean same = source != null && source.equals(target);
        sourceUndeclared.set(brief != null && source == null);
        sameLanguage.set(same);
        targetUntested.set(target != null && !languages.hasTestedRules(target));
        canContinue.set(source != null && target != null && !same);
        log.debug("languages {} -> {}: same {}, untested {}", source, target, same, targetUntested.get());
    }
}
