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
import java.util.function.UnaryOperator;
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
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
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
    private final Messages messages;
    private final GlossaryFiles files;
    private final ObservableList<GlossaryEntry> rows = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<@Nullable GlossaryNotice> notice = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper restorations = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper suggestedCount = new ReadOnlyIntegerWrapper();
    private final GlossaryAdditions additions;
    private String projectId = "";
    private long generation;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param glossary the port the glossary is read and edited through
     * @param models where the model of a model scan is built
     * @param settings where the chosen provider and model are read
     * @param executor the daemon executor every call runs on, never the FX thread
     * @param messages the catalogue the refusals are worded from
     * @param activities the model work under way, which the model scan and review must not overlap
     */
    @Inject
    public NamesStyleViewModel(
            final GlossaryService glossary,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final @BackgroundExecutor ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities) {
        this(
                glossary,
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
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final ExecutorService executor,
            final Messages messages,
            final ActivityTracker activities,
            final Supplier<String> ids) {
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.calls = new GlossaryCalls(executor);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.modelRuns = new GlossaryModelRuns(
                new GlossaryModelRuns.Screen(
                        glossary,
                        models,
                        settings,
                        messages,
                        rows,
                        notice,
                        () -> projectId,
                        Objects.requireNonNull(activities, "activities")),
                calls);
        this.files = new GlossaryFiles(glossary, calls, rows, notice, new ImportSummary(messages));
        this.additions = new GlossaryAdditions(
                glossary, calls, rows, messages, Objects.requireNonNull(ids, "ids"), () -> projectId);
        rows.addListener((ListChangeListener<GlossaryEntry>) change -> suggestedCount.set(
                (int) rows.stream().filter(GlossaryEntry::isSuggested).count()));
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

    /**
     * Whether a model scan or review is under way.
     *
     * @return a read-only property; the model buttons are not offered while it is {@code true}, the stop button is
     */
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
     * How many refused edits have been undone.
     *
     * @return a read-only counter that only ever grows
     */
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
        projectId = project;
        final long ticket = ++generation;
        log.debug("showing the glossary of project {} (opening {})", project, ticket);
        rows.clear();
        notice.set(null);
        calls.run(
                "load",
                () -> GlossaryLoad.loadOrScan(glossary, project),
                answer -> ifCurrent(ticket, () -> settle("load", answer, all -> rows.setAll(all))));
    }

    /**
     * Sets a row's target; a blank text clears it.
     *
     * @param entryId the row's entry id
     * @param text the typed target
     */
    public void setTarget(final String entryId, final String text) {
        Objects.requireNonNull(text, "text");
        change(entryId, "target", held -> GlossaryEdits.target(held, text.isBlank() ? null : text.strip()));
    }

    /**
     * Sets a row's lock.
     *
     * @param entryId the row's entry id
     * @param locked whether the term is locked
     */
    public void setLocked(final String entryId, final boolean locked) {
        change(entryId, "locked", held -> GlossaryEdits.locked(held, locked));
    }

    /**
     * Sets a row's type.
     *
     * @param entryId the row's entry id
     * @param type the chosen type
     */
    public void setType(final String entryId, final TermType type) {
        Objects.requireNonNull(type, "type");
        change(entryId, "type", held -> GlossaryEdits.type(held, type));
    }

    /**
     * Sets a row's gender.
     *
     * @param entryId the row's entry id
     * @param gender the chosen gender
     */
    public void setGender(final String entryId, final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        change(entryId, "gender", held -> GlossaryEdits.gender(held, gender));
    }

    private void change(final String entryId, final String field, final UnaryOperator<GlossaryEntry> edit) {
        Objects.requireNonNull(entryId, "entryId");
        final int at = GlossaryEdits.indexOf(rows, entryId);
        if (at < 0) {
            log.debug("edit of {} on entry {} ignored: no such row", field, entryId);
            return;
        }
        final GlossaryEntry changed = edit.apply(rows.get(at));
        if (changed.equals(rows.get(at))) {
            log.debug("edit of {} on entry {} ignored: nothing changed", field, entryId);
            return;
        }
        log.debug("editing {} of entry {}", field, entryId);
        log.trace("entry {} becomes term '{}' target '{}'", entryId, changed.term(), changed.target());
        final long ticket = generation;
        calls.run(
                "update", () -> glossary.update(changed), answer -> ifCurrent(ticket, () -> updated(entryId, answer)));
    }

    private void updated(final String entryId, final Result<GlossaryEntry> answer) {
        final int at = GlossaryEdits.indexOf(rows, entryId);
        final AppError failure = answer.error();
        if (failure == null) {
            if (at >= 0) {
                rows.set(at, Objects.requireNonNull(answer.data(), "data"));
            }
            notice.set(null);
            return;
        }
        refuse(entryId, failure);
        if (at >= 0) {
            rows.set(at, rows.get(at));
        }
        restorations.set(restorations.get() + 1);
    }

    private void refuse(final String entryId, final AppError failure) {
        if (failure.code() == ErrorCode.validation) {
            log.warn("edit of entry {} refused: a locked term needs a target", entryId);
            error(messages.get(MessageKey.NAMES_STYLE_LOCK_NEEDS_TARGET));
            return;
        }
        fail("update", failure);
    }

    /**
     * Removes a row's term from the glossary.
     *
     * @param entryId the row's entry id
     */
    public void remove(final String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        log.debug("removing entry {} of project {}", entryId, projectId);
        final String project = projectId;
        final long ticket = generation;
        calls.run(
                "remove",
                () -> glossary.remove(project, entryId),
                answer -> ifCurrent(
                        ticket,
                        () -> settle(
                                "remove",
                                answer,
                                gone -> rows.removeIf(row -> row.id().equals(entryId)))));
    }

    /** Runs the model name scan with the chosen model; the rows are kept and the proposals join them. */
    public void modelScan() {
        modelRuns.scan();
    }

    /** Asks the chosen model to review the unlocked rows with no target, then shows the glossary as it left it. */
    public void review() {
        modelRuns.review();
    }

    /** Stops the model scan or review under way; the glossary keeps what it had. */
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
        change(entryId, "accept", GlossaryEntry::accepted);
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

    /**
     * How many rows hold a suggested target nobody confirmed.
     *
     * @return a read-only count that follows the rows
     */
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

    private void error(final String text) {
        notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, text));
    }

    private <T> void settle(final String what, final Result<T> answer, final Consumer<T> onOk) {
        final AppError failure = answer.error();
        if (failure != null) {
            fail(what, failure);
            return;
        }
        onOk.accept(Objects.requireNonNull(answer.data(), "data"));
    }

    private void fail(final String what, final AppError failure) {
        log.warn("the glossary {} failed with {}", what, failure.code());
        error(failure.message());
    }
}
