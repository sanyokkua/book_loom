package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
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
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;
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
    private final AutoEstimate autoEstimate;
    private String projectId = "";
    private final Openings openings = new Openings();

    /**
     * Receives the collaborators the injector owns.
     *
     * @param glossary the port the glossary is read and edited through
     * @param lexicon the port the recurring terms are read and edited through
     * @param namesPass the port that prepares both tables in one pass
     * @param capabilities where the chosen model's context length is detected for that pass
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
            final NamesPass namesPass,
            final ModelCapabilities capabilities,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final @BackgroundExecutor ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities) {
        this(
                glossary,
                lexicon,
                namesPass,
                capabilities::detectedTokens,
                models,
                settings,
                executor,
                messages,
                activities,
                () -> UUID.randomUUID().toString());
    }

    // The id source is a seam so that a test states the ids it expects, and no provider is asked for a context length.
    NamesStyleViewModel(
            final GlossaryService glossary,
            final LexiconService lexicon,
            final NamesPass namesPass,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities,
            final Supplier<String> ids) {
        this(
                glossary,
                lexicon,
                namesPass,
                _ -> Optional.empty(),
                models,
                settings,
                executor,
                messages,
                activities,
                ids);
    }

    private NamesStyleViewModel(
            final GlossaryService glossary,
            final LexiconService lexicon,
            final NamesPass namesPass,
            final Function<ModelSelection, Optional<Integer>> detection,
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
        this.autoEstimate = new AutoEstimate(namesPass, calls);
        this.modelRuns = newModelRuns(lexicon, namesPass, detection, models, settings, messages, activities);
        this.recurring = new RecurringTerms(
                new LexiconActions(lexicon, calls, lexiconRows, rows, messages, notice, new ImportSummary(messages)),
                () -> projectId,
                openings::whileCurrent,
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
            final NamesPass namesPass,
            final Function<ModelSelection, Optional<Integer>> detection,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final Messages messages,
            final ActivityTracker activities) {
        return new GlossaryModelRuns(
                new GlossaryModelRuns.Screen(
                        glossary,
                        Objects.requireNonNull(lexicon, "lexicon"),
                        namesPass,
                        detection,
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
        final long ticket = openings.begin();
        log.debug("showing the glossary of project {} (opening {})", project, ticket);
        rows.clear();
        notice.set(null);
        if (!sameProject) {
            changeLog.begin();
            autoEstimate.clear();
        }
        recurring.show(project);
        calls.run(
                "load",
                () -> GlossaryLoad.loadOrScan(glossary, project),
                answer -> openings.ifCurrent(ticket, () -> GlossaryLoad.show(answer, rows, notice)));
        refreshAutoEstimate();
    }

    /** Sets a row's target; a blank {@code text} clears it. */
    public void setTarget(final String entryId, final String text) {
        Objects.requireNonNull(text, "text");
        edits.change(
                entryId,
                "target",
                held -> GlossaryEdits.target(held, text.isBlank() ? null : text.strip()),
                openings.whileCurrent());
    }

    /** Locks or unlocks a row's term. */
    public void setLocked(final String entryId, final boolean locked) {
        edits.change(entryId, "locked", held -> GlossaryEdits.locked(held, locked), openings.whileCurrent());
    }

    /** Sets a row's type. */
    public void setType(final String entryId, final TermType type) {
        Objects.requireNonNull(type, "type");
        edits.change(entryId, "type", held -> GlossaryEdits.type(held, type), openings.whileCurrent());
    }

    /** Sets a row's gender. */
    public void setGender(final String entryId, final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        edits.change(entryId, "gender", held -> GlossaryEdits.gender(held, gender), openings.whileCurrent());
    }

    /** Removes a row's term from the glossary. */
    public void remove(final String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        edits.remove(projectId, entryId, openings.whileCurrent());
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

    /** Prepares both tables with the chosen model in one pass; Stop ends it and changes nothing. */
    public void autoPrepare() {
        modelRuns.autoNames();
    }

    /** {@return the pass's estimate for the project's dial, or null while unknown or when the port refused} */
    public ReadOnlyObjectProperty<@Nullable NamesEstimate> autoEstimate() {
        return autoEstimate.property();
    }

    /** Asks the port for the estimate again, as when the dial changed. */
    public void refreshAutoEstimate() {
        autoEstimate.refresh(projectId, openings.whileCurrent());
    }

    /** Stops the model scan, review or translation under way; the glossary keeps what it had. */
    public void stopModel() {
        modelRuns.stop();
    }

    /** Imports the CSV file the person chose, then shows the glossary as the import left it. */
    public void importCsv(final Path source) {
        Objects.requireNonNull(source, "source");
        files.importCsv(projectId, source, openings.whileCurrent());
    }

    /** Writes the glossary to the CSV file the person chose. */
    public void exportCsv(final Path destination) {
        Objects.requireNonNull(destination, "destination");
        files.exportCsv(projectId, destination, openings.whileCurrent());
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

    /** Confirms a row's suggested target as the person's, so it is applied exactly and never replaced by the model. */
    public void accept(final String entryId) {
        edits.change(entryId, "accept", GlossaryEntry::accepted, openings.whileCurrent());
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
}
