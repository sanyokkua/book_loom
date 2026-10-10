package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.Messages;

/**
 * What the names and style screen shows and does with the open project's glossary: the rows, the line reported in
 * place, and every call to {@link GlossaryService}, which runs on the background executor and is published on the FX
 * thread.
 *
 * <p>A refused edit leaves the stored row as it was and raises {@link #restorations()}, so a cell showing what the
 * person typed learns to show the stored value again. Opening the screen supersedes any earlier opening's calls still
 * in flight. FX thread only.
 */
@Slf4j
@Singleton
public final class NamesStyleViewModel {

    private final GlossaryService glossary;
    private final GlossaryCalls calls;
    private final GlossaryModelRuns modelRuns;
    private final GlossaryFiles files;
    private final ObservableList<GlossaryEntry> rows = FXCollections.observableArrayList();
    private final ObservableList<LexiconEntry> lexiconRows = FXCollections.observableArrayList();
    private final RecurringTerms recurring;
    private final GlossaryRowEdits edits;
    private final ReadOnlyObjectWrapper<@Nullable GlossaryNotice> notice = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper restorations = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper suggestedCount = new ReadOnlyIntegerWrapper();
    private final GlossaryAdditions additions;
    private final ChangeMarks marks = new ChangeMarks();
    private final ChangeLog changeLog;
    private String projectId = "";
    private long generation;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param glossary the port the glossary is read and edited through
     * @param lexicon the port the recurring terms are read and edited through
     * @param models where the model of a model scan is built
     * @param settings where the chosen provider and model are read
     * @param executor the daemon executor every call runs on, never the FX thread
     * @param messages the catalogue the refusals are worded from
     * @param activities the model work under way, which the model scan and review must not overlap
     */
    @Inject
    public NamesStyleViewModel(
            final GlossaryService glossary,
            final LexiconService lexicon,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final @BackgroundExecutor ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities) {
        this(
                glossary,
                lexicon,
                models,
                settings,
                executor,
                messages,
                activities,
                () -> UUID.randomUUID().toString());
    }

    // The id source is a seam so that a test states the ids it expects.
    NamesStyleViewModel(
            final GlossaryService glossary,
            final LexiconService lexicon,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities,
            final Supplier<String> ids) {
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.calls = new GlossaryCalls(executor);
        this.changeLog = new ChangeLog(
                new ChangeReverts(
                        glossary, lexicon, calls, rows, lexiconRows, marks, notice, () -> projectId, messages),
                marks,
                messages,
                Objects.requireNonNull(activities, "activities"));
        this.modelRuns = newModelRuns(lexicon, models, settings, messages, activities);
        this.recurring = new RecurringTerms(
                new LexiconActions(lexicon, calls, lexiconRows, rows, messages, notice),
                () -> projectId,
                this::whileCurrent,
                new RecurringTerms.ModelActions(
                        modelRuns::scanTerms, modelRuns::reviewTerms, modelRuns::translateTerms));
        this.edits = new GlossaryRowEdits(glossary, calls, rows, messages, notice, restorations);
        this.files = new GlossaryFiles(glossary, calls, rows, notice, new ImportSummary(messages));
        this.additions = new GlossaryAdditions(
                glossary, calls, rows, messages, Objects.requireNonNull(ids, "ids"), () -> projectId);
        rows.addListener((ListChangeListener<GlossaryEntry>) change -> suggestedCount.set(
                (int) rows.stream().filter(GlossaryEntry::isSuggested).count()));
    }

    private GlossaryModelRuns newModelRuns(
            final LexiconService lexicon,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final Messages messages,
            final ActivityTracker activities) {
        return new GlossaryModelRuns(
                new GlossaryModelRuns.Screen(
                        glossary,
                        Objects.requireNonNull(lexicon, "lexicon"),
                        models,
                        settings,
                        messages,
                        rows,
                        lexiconRows,
                        notice,
                        () -> projectId,
                        activities,
                        changeLog),
                calls);
    }

    /**
     * The glossary rows, in the order the service listed them, proposals after them.
     *
     * @return a read-only list
     */
    public ObservableList<GlossaryEntry> rows() {
        return FXCollections.unmodifiableObservableList(rows);
    }

    /**
     * The line reported in place.
     *
     * @return a read-only property holding {@code null} when there is nothing to report
     */
    public ReadOnlyObjectProperty<@Nullable GlossaryNotice> notice() {
        return notice.getReadOnlyProperty();
    }

    /** {@return a read-only flag, true while a model scan, review or translation is under way and Stop is offered} */
    public ReadOnlyBooleanProperty busy() {
        return modelRuns.busy();
    }

    /**
     * Why the model scan and review are not offered while other model work runs.
     *
     * @return a read-only property, empty while they are offered or one of them runs itself
     */
    public ReadOnlyStringProperty modelBlockedReason() {
        return modelRuns.blockedReason();
    }

    /**
     * What the last model operation did, exactly, for the results dialog to open on.
     *
     * @return a read-only property holding the results of the newest operation, or {@code null} while one runs or none
     *     has finished since the screen opened
     */
    public ReadOnlyObjectProperty<@Nullable ChangeResults> results() {
        return changeLog.results();
    }

    /**
     * The rows the last operation added or changed, for their marker and the "Changed in last run" filter.
     *
     * @return the marks, which the next operation clears
     */
    public ChangeMarks marks() {
        return marks;
    }

    /**
     * The recurring terms the Recurring terms card shows.
     *
     * @return the card's state, which lives as long as this view model
     */
    public RecurringTerms recurring() {
        return recurring;
    }

    /** {@return a read-only count of the refused edits that have been undone, which only ever grows} */
    public ReadOnlyIntegerProperty restorations() {
        return restorations.getReadOnlyProperty();
    }

    /**
     * Loads a project's glossary and, when it is empty, runs the deterministic scan.
     *
     * @param project the stored project whose glossary is shown
     */
    public void show(final String project) {
        Objects.requireNonNull(project, "project");
        final boolean sameProject = project.equals(projectId);
        projectId = project;
        final long ticket = ++generation;
        log.debug("showing the glossary of project {} (opening {})", project, ticket);
        rows.clear();
        notice.set(null);
        if (!sameProject) {
            changeLog.begin();
        }
        recurring.show(project);
        calls.run(
                "load",
                () -> GlossaryLoad.loadOrScan(glossary, project),
                answer -> ifCurrent(ticket, () -> loaded(answer)));
    }

    /**
     * Sets a row's target; a blank text clears it.
     *
     * @param entryId the row's entry id
     * @param text the typed target
     */
    public void setTarget(final String entryId, final String text) {
        Objects.requireNonNull(text, "text");
        edits.change(
                entryId,
                "target",
                held -> GlossaryEdits.target(held, text.isBlank() ? null : text.strip()),
                whileCurrent());
    }

    /**
     * Sets a row's lock.
     *
     * @param entryId the row's entry id
     * @param locked whether the term is locked
     */
    public void setLocked(final String entryId, final boolean locked) {
        edits.change(entryId, "locked", held -> GlossaryEdits.locked(held, locked), whileCurrent());
    }

    /**
     * Sets a row's type.
     *
     * @param entryId the row's entry id
     * @param type the chosen type
     */
    public void setType(final String entryId, final TermType type) {
        Objects.requireNonNull(type, "type");
        edits.change(entryId, "type", held -> GlossaryEdits.type(held, type), whileCurrent());
    }

    /**
     * Sets a row's gender.
     *
     * @param entryId the row's entry id
     * @param gender the chosen gender
     */
    public void setGender(final String entryId, final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        edits.change(entryId, "gender", held -> GlossaryEdits.gender(held, gender), whileCurrent());
    }

    /**
     * Removes a row's term from the glossary.
     *
     * @param entryId the row's entry id
     */
    public void remove(final String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        edits.remove(projectId, entryId, whileCurrent());
    }

    /** Runs the model name scan with the chosen model; the rows are kept and the proposals join them. */
    public void modelScan() {
        modelRuns.scan();
    }

    /** Asks the chosen model to review the unlocked rows with no target, then shows the glossary as it left it. */
    public void review() {
        modelRuns.review();
    }

    /** Asks the chosen model for a target of each row that has none, and changes nothing else. */
    public void translate() {
        modelRuns.translate();
    }

    /** Stops the model scan, review or translation under way; the glossary keeps what it had. */
    public void stopModel() {
        modelRuns.stop();
    }

    /**
     * Imports a CSV file, then shows the glossary as the import left it.
     *
     * @param source the file the person chose
     */
    public void importCsv(final Path source) {
        Objects.requireNonNull(source, "source");
        files.importCsv(projectId, source, whileCurrent());
    }

    /**
     * Writes the glossary to a CSV file.
     *
     * @param destination the file the person chose
     */
    public void exportCsv(final Path destination) {
        Objects.requireNonNull(destination, "destination");
        files.exportCsv(projectId, destination, whileCurrent());
    }

    /**
     * Adds a term, refusing a blank source, a duplicate or a lock with no target before asking the service.
     *
     * @param term what the Add term card collected
     * @param onAdded run on the FX thread once the term is stored and shown
     * @param onRefused run on the FX thread with the reason, in the display language, the term was not added
     */
    public void add(final NewTerm term, final Runnable onAdded, final Consumer<String> onRefused) {
        additions.add(term, onAdded, onRefused);
    }

    /**
     * Confirms a row's suggested target as the person's, so it is applied exactly and never replaced by the model.
     *
     * @param entryId the row's entry id
     */
    public void accept(final String entryId) {
        edits.change(entryId, "accept", GlossaryEntry::accepted, whileCurrent());
    }

    /** Confirms every row's suggested target, each as {@link #accept(String)} would. */
    public void acceptAll() {
        final List<String> suggested = rows.stream()
                .filter(GlossaryEntry::isSuggested)
                .map(GlossaryEntry::id)
                .toList();
        log.info("accepting {} suggested targets of project {}", suggested.size(), projectId);
        suggested.forEach(this::accept);
    }

    /** {@return a read-only count of the rows that hold a suggested target nobody confirmed} */
    public ReadOnlyIntegerProperty suggestedCount() {
        return suggestedCount.getReadOnlyProperty();
    }

    /** Publishes an answer only while the opening that asked for it is still the screen's current one. */
    private Consumer<Runnable> whileCurrent() {
        final long ticket = generation;
        return publish -> ifCurrent(ticket, publish);
    }

    private void ifCurrent(final long ticket, final Runnable publish) {
        if (ticket == generation) {
            publish.run();
        } else {
            log.debug("an answer of opening {} is dropped: opening {} superseded it", ticket, generation);
        }
    }

    private void loaded(final Result<List<GlossaryEntry>> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            log.warn("the glossary load failed with {}", failure.code());
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
            return;
        }
        rows.setAll(Objects.requireNonNull(answer.data(), "data"));
    }
}
