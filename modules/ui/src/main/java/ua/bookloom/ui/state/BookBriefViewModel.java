package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
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
    private final BriefLanguageFlags flags;
    private final NarratorNotice narratorNotice;
    private final StyleSuggestion style;
    private final BriefPolicyKeys policyKeys;

    /**
     * Follows the open book's brief.
     *
     * @param project the holder of the open book and its brief, which outlives this view model
     * @param projects the port a changed brief is saved through
     * @param executor the daemon executor a save runs on, never the FX thread
     * @param languages tells whether the chosen target language has tested translation rules
     * @param setup asks the model for a style, or {@code null} for a window with none
     * @param outcomes hears what each save was answered, so a refused language pair is asked about
     */
    @Inject
    public BookBriefViewModel(
            final CurrentProject project,
            final ProjectService projects,
            @BackgroundExecutor final ExecutorService executor,
            final LanguageSupport languages,
            final @Nullable SetupHelper setup,
            final BriefSaveOutcomes outcomes) {
        this.style = new StyleSuggestion(project, setup, (what, op) -> change(what, "applied", op));
        this.project = Objects.requireNonNull(project, "project");
        this.flags = new BriefLanguageFlags(languages);
        this.saver = new BriefSaver(
                Objects.requireNonNull(projects, "projects"),
                Objects.requireNonNull(executor, "executor"),
                Objects.requireNonNull(outcomes, "outcomes"));
        this.policyKeys = new BriefPolicyKeys(languages, this::change);
        project.brief().addListener((observed, was, now) -> derive(now));
        derive(project.brief().get());
        this.narratorNotice = new NarratorNotice(project, this::setNarratorPerson);
        log.debug(
                "book brief created, a book is already open: {}", project.book().get() != null);
    }

    /** A view model that hears no saved answer, as the tests of the brief alone build it. */
    public BookBriefViewModel(
            final CurrentProject project,
            final ProjectService projects,
            final ExecutorService executor,
            final LanguageSupport languages,
            final @Nullable SetupHelper setup) {
        this(project, projects, executor, languages, setup, BriefSaveOutcomes.NONE);
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
     * Whether a book is open whose brief has no source language (Auto): the book declares none the application
     * recognises and none has been chosen, so the model identifies it.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty sourceUndeclared() {
        return flags.sourceUndeclared();
    }

    /**
     * Whether the source and the target language are the same language.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty sameLanguage() {
        return flags.sameLanguage();
    }

    /**
     * Whether the chosen target has no tested translation rules, so the general ones are used.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty targetUntested() {
        return flags.targetUntested();
    }

    /**
     * Whether the brief can go on to the next step: both languages are chosen and they differ.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty canContinue() {
        return flags.canContinue();
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
     * Chooses Auto as the source language: the brief names none and the model identifies the language from the text.
     * Nothing changes, and nothing is saved, when the source already is Auto.
     */
    public void setSourceAuto() {
        change("source language", "auto", brief -> brief.withLanguages(null, brief.targetLanguage()));
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
        final String text = BriefRebuild.blankToNull(genre);
        log.trace("genre is now '{}'", text);
        change("genre", text == null ? "cleared" : "set", BriefField.GENRE, brief -> brief.withGenre(text));
    }

    /**
     * Sets the narrative voice and era notes.
     *
     * @param voiceEra free text; blank clears it
     */
    public void setVoiceEra(final String voiceEra) {
        final String text = BriefRebuild.blankToNull(voiceEra);
        log.trace("voice and era are now '{}'", text);
        change(
                "voice and era",
                text == null ? "cleared" : "set",
                BriefField.VOICE_ERA,
                brief -> brief.withVoiceEra(text));
    }

    /**
     * Sets the audience.
     *
     * @param audience free text; blank clears it
     */
    public void setAudience(final String audience) {
        final String text = BriefRebuild.blankToNull(audience);
        log.trace("audience is now '{}'", text);
        change("audience", text == null ? "cleared" : "set", BriefField.AUDIENCE, brief -> brief.withAudience(text));
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
                BriefField.REGISTER,
                brief -> BriefRebuild.of(brief, register, null, null, null, null, null, null));
    }

    /** Sets how character and place names are rendered. */
    public void setNames(final NamePolicy names) {
        Objects.requireNonNull(names, "names");
        change("name policy", names.name(), brief -> BriefRebuild.of(brief, null, names, null, null, null, null, null));
    }

    /** Sets how foreign-language passages are handled. */
    public void setForeignPassages(final ForeignPassagePolicy foreign) {
        Objects.requireNonNull(foreign, "foreign");
        change(
                "foreign-passage policy",
                foreign.name(),
                brief -> BriefRebuild.of(brief, null, null, foreign, null, null, null, null));
    }

    /** Sets how footnotes are handled. */
    public void setFootnotes(final FootnotePolicy footnotes) {
        Objects.requireNonNull(footnotes, "footnotes");
        change(
                "footnote policy",
                footnotes.name(),
                brief -> BriefRebuild.of(brief, null, null, null, footnotes, null, null, null));
    }

    /** Sets how units of measurement are handled. */
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
        changeNarrator("narrator person", person.name(), narrator -> narrator.withPerson(person));
    }

    /**
     * Sets the narrator's gender, which a first-person narrator's verbs must agree with; the person is kept.
     *
     * @param gender the narrator's gender, or {@link Gender#UNKNOWN} when not stated
     */
    public void setNarratorGender(final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        changeNarrator("narrator gender", gender.name(), narrator -> new Narrator(narrator.person(), gender));
    }

    /**
     * Sets a first-person narrator of the given gender in one change, as the Start translation question does.
     *
     * @param gender the narrator's gender
     */
    public void setFirstPersonNarrator(final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        changeNarrator("first-person narrator", gender.name(), narrator -> new Narrator(NarratorPerson.FIRST, gender));
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

    private void change(final String field, final String value, final UnaryOperator<BookBrief> edit) {
        change(field, value, null, edit);
    }

    private void changeNarrator(final String field, final String value, final UnaryOperator<Narrator> edit) {
        change(field, value, BriefField.NARRATOR, brief -> brief.withNarrator(edit.apply(brief.narrator())));
    }

    // A field the person edits is no longer a guess: it leaves the likely set whatever the edit made of it.
    private void change(
            final String field,
            final String value,
            final @Nullable BriefField edited,
            final UnaryOperator<BookBrief> edit) {
        final OpenedBook book = project.book().get();
        final BookBrief current = project.brief().get();
        if (book == null || current == null) {
            log.debug("{} not changed: no book is open", field);
            return;
        }
        final BookBrief changed = BriefRebuild.confirmed(edit.apply(current), edited);
        log.debug("{} is now {}", field, value);
        if (changed.equals(current)) {
            log.debug("{} was already so; nothing is saved", field);
            return;
        }
        project.replaceBrief(changed);
        saver.enqueue(book.projectId(), changed);
    }

    private void derive(final @Nullable BookBrief brief) {
        flags.derive(brief);
        policyKeys.derive(brief);
    }

    /** The apostrophe, quotation-mark, address and convention choices the target offers, and their setters. */
    public BriefPolicyKeys policyKeys() {
        return policyKeys;
    }

    /**
     * The model's proposal of the tone and style fields.
     *
     * @return the proposal, whose {@code ask()} fills the fields from the opening of the book
     */
    public StyleSuggestion styleSuggestion() {
        return style;
    }
}
