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
import javafx.collections.ObservableMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TargetState;
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
    private final GenderConfirmations genderConfirmations;
    private final TargetAcceptances acceptances;
    private final ReviewFlagsMirror flags;
    private final GlossaryAdditions additions;
    private final ChangeMarks marks = new ChangeMarks();
    private final ChangeLog changeLog;
    private final ResultsReview review = new ResultsReview();
    private final AutoEstimate autoEstimate;
    private String projectId = "";
    private final InFlight loads = new InFlight();
    private final Openings openings = new Openings();

    /**
     * Receives the collaborators the injector owns: every call runs on the daemon {@code executor}, never the FX thread;
     * {@code activities} is the model work the scan and review must not overlap; {@code measured} holds the seconds per
     * call measured on the chosen model, read for and stored by the names pass.
     */
    @Inject
    public NamesStyleViewModel(
            final NamesServices services,
            final ModelAccess access,
            final @BackgroundExecutor ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities,
            final MeasuredCalls measured) {
        this(
                services,
                access,
                executor,
                messages,
                activities,
                () -> UUID.randomUUID().toString(),
                measured);
    }

    // The id source is a seam so that a test states the ids it expects, and no provider is asked for a context length.
    NamesStyleViewModel(
            final NamesServices services,
            final ModelAccess access,
            final ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities,
            final Supplier<String> ids) {
        this(services, access, executor, messages, activities, ids, MeasuredCalls.none());
    }

    private NamesStyleViewModel(
            final NamesServices services,
            final ModelAccess access,
            final ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities,
            final Supplier<String> ids,
            final MeasuredCalls measured) {
        this.glossary = Objects.requireNonNull(services, "services").glossary();
        final LexiconService lexicon = services.lexicon();
        this.calls = new GlossaryCalls(executor);
        this.changeLog = new ChangeLog(
                new ChangeReverts(
                        glossary, lexicon, calls, rows, lexiconRows, marks, notice, () -> projectId, messages),
                marks,
                messages,
                Objects.requireNonNull(activities, "activities"));
        this.autoEstimate = new AutoEstimate(services.namesPass(), calls, access.settings(), measured);
        this.modelRuns = newModelRuns(services, access, messages, activities, measured);
        this.recurring = new RecurringTerms(
                new LexiconActions(lexicon, calls, lexiconRows, rows, messages, notice, new ImportSummary(messages)),
                () -> projectId,
                openings::whileCurrent,
                new RecurringTerms.ModelActions(
                        modelRuns::scanTerms, modelRuns::reviewTerms, modelRuns::translateTerms));
        this.edits = new GlossaryRowEdits(glossary, calls, rows, messages, notice, restorations);
        this.files = new GlossaryFiles(glossary, calls, rows, notice, new ImportSummary(messages), messages);
        this.additions = new GlossaryAdditions(
                glossary, calls, rows, messages, Objects.requireNonNull(ids, "ids"), () -> projectId);
        this.genderConfirmations =
                new GenderConfirmations(glossary, calls, rows, notice, changeLog, () -> projectId, messages);
        this.acceptances = new TargetAcceptances(glossary, calls, rows, notice, changeLog, () -> projectId, messages);
        this.flags = new ReviewFlagsMirror(glossary, calls, rows, () -> projectId);
        rows.addListener((ListChangeListener<GlossaryEntry>) change -> suggestedCount.set(
                (int) rows.stream().filter(GlossaryEntry::isSuggested).count()));
    }

    private GlossaryModelRuns newModelRuns(
            final NamesServices services,
            final ModelAccess access,
            final Messages messages,
            final ActivityTracker activities,
            final MeasuredCalls measured) {
        return new GlossaryModelRuns(
                new GlossaryModelRuns.Screen(
                        glossary,
                        services.lexicon(),
                        services.namesPass(),
                        access.detection(),
                        access.models(),
                        access.settings(),
                        messages,
                        rows,
                        lexiconRows,
                        notice,
                        () -> projectId,
                        activities,
                        changeLog,
                        measured),
                calls);
    }

    /** {@return a read-only list of the glossary rows, in the order the service listed them, proposals after them} */
    public ObservableList<GlossaryEntry> rows() {
        return FXCollections.unmodifiableObservableList(rows);
    }

    /** {@return a read-only property of the line reported in place, {@code null} when there is nothing to report} */
    public ReadOnlyObjectProperty<@Nullable GlossaryNotice> notice() {
        return notice.getReadOnlyProperty();
    }

    /** {@return a read-only flag, true while a model scan, review or translation is under way and Stop is offered} */
    public ReadOnlyBooleanProperty busy() {
        return modelRuns.busy();
    }

    /** {@return why the model scan and review are not offered while other model work runs; empty while offered} */
    public ReadOnlyStringProperty modelBlockedReason() {
        return modelRuns.blockedReason();
    }

    /** {@return what the last model operation did, for the results dialog; null while one runs or none finished} */
    public ReadOnlyObjectProperty<@Nullable ChangeResults> results() {
        return changeLog.results();
    }

    /** {@return the results of a pass that ended in the background, from the message that offers them to their opening} */
    public ResultsReview resultsReview() {
        return review;
    }

    /** {@return the marks of the rows the last operation added or changed, which the next operation clears} */
    public ChangeMarks marks() {
        return marks;
    }

    /** {@return the Recurring terms card's state, which lives as long as this view model} */
    public RecurringTerms recurring() {
        return recurring;
    }

    /** {@return a read-only count of the refused edits that have been undone, which only ever grows} */
    public ReadOnlyIntegerProperty restorations() {
        return restorations.getReadOnlyProperty();
    }

    /** Loads the stored project's glossary and, when it is empty, runs the deterministic scan. */
    public void show(final String project) {
        Objects.requireNonNull(project, "project");
        final boolean sameProject = project.equals(projectId);
        if (!loads.begin(project)) {
            return;
        }
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
        calls.run("load", () -> GlossaryLoad.loadOrScan(glossary, project), answer -> {
            loads.end(project);
            openings.ifCurrent(ticket, () -> edits.loaded(answer));
        });
        refreshAutoEstimate();
    }

    /** Sets a row's target; a blank {@code text} clears it. */
    public void setTarget(final String entryId, final String text) {
        Objects.requireNonNull(text, "text");
        edits.change(entryId, "target", held -> GlossaryEdits.target(held, text), openings.whileCurrent());
    }

    /** Sets a row's State, which makes its target the person's; a row with no target is refused. */
    public void setState(final String entryId, final TargetState state) {
        Objects.requireNonNull(state, "state");
        edits.state(entryId, state, openings.whileCurrent());
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

    /** Forgets the estimate, as the dial it was computed for has changed and the new one is not known yet. */
    public void clearAutoEstimate() {
        autoEstimate.clear();
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

    /** Accepts every suggested target that passes the target language's checks; the results list the rest. */
    public void acceptAll() {
        acceptances.acceptAll();
    }

    /** {@return the review flags of the shown rows by entry id, computed by the service and never stored} */
    public ObservableMap<String, List<TargetIssue>> reviewFlags() {
        return flags.flags();
    }

    /** {@return a read-only count of the times the review flags were read again, which only ever grows} */
    public ReadOnlyIntegerProperty reviewFlagRefreshes() {
        return flags.refreshes();
    }

    /** {@return a read-only count of the rows that hold a suggested target nobody confirmed} */
    public ReadOnlyIntegerProperty suggestedCount() {
        return suggestedCount.getReadOnlyProperty();
    }

    /** Turns every suggested character gender into the person's; the results list can put each back. */
    public void confirmGenders() {
        genderConfirmations.confirmAll();
    }

    /** {@return a read-only count of the characters whose gender is a suggestion nobody has confirmed} */
    public ReadOnlyIntegerProperty suggestedGenderCount() {
        return genderConfirmations.suggestedCount();
    }
}
