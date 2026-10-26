package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.util.paths.DestinationPath;

/**
 * Where the translation is written, what is written beside it, whether the last check runs, and the export itself.
 *
 * <p>A singleton, like {@link CurrentProject}, because the screens are rebuilt on every visit while the choices must
 * survive them; it proposes a destination for a book that is already open when it is first built. {@link
 * ExportProposals} builds the name through {@link DestinationPath}, shared with the command line, and the proposal
 * follows the brief's target language until the person has picked a path.
 *
 * <p>Looking for files at the destination is I/O, so it runs on the background executor and its answer is published
 * back with {@code Platform.runLater}; an answer about an older question is dropped. Pressing Export book looks again,
 * off the FX thread, and asks before replacing ({@link ReplaceCheck}). Everything else is touched on the FX thread.
 */
@Slf4j
@Singleton
public final class ExportViewModel {

    private final CurrentProject project;
    private final Messages messages;
    private final Clock clock;
    private final ExportAnnouncer announcer;
    private final ObservableSet<SideFile> sideFiles =
            FXCollections.observableSet(EnumSet.of(SideFile.GLOSSARY_CSV, SideFile.QUALITY_REPORT));
    private final ReadOnlyStringWrapper refusal = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper currentNote = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper runNote = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper blockReason = new ReadOnlyStringWrapper("");
    private final ObjectBinding<@Nullable ActivityKind> blocker;
    private final ReadOnlyBooleanWrapper exportAvailable = new ReadOnlyBooleanWrapper(false);
    private final ExportProposals proposals;
    private final ExportStatement statement;
    private final ExportOccupancy occupancy;
    private final ExportRun run;
    private final LastCheckChoice lastCheck;
    public final NameSuggestion nameSuggestion; // the model's proposal of the translated book's file name
    private final ReadOnlyStringWrapper destination = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper destinationExists = new ReadOnlyBooleanWrapper(false);
    // Whether the person, and not a proposal, chose the destination. FX thread only.
    private boolean destinationEdited;

    /**
     * Follows the open book and its brief and proposes a destination for a book already open. Reads and the export run
     * on {@code executor}; {@code setup} is null for a window with no model proposals; {@code stored} is only read and
     * {@code store} keeps the last written export; {@code announcer} says when a book was written.
     */
    @Inject
    public ExportViewModel(
            final CurrentProject project,
            final ReviewDesk desk,
            final ProjectService projects,
            final Messages messages,
            final ExportService exports,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final WorkflowProgress progress,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities,
            final @Nullable SetupHelper setup,
            final SettingsRepository stored,
            final ProjectRepository store,
            final ConfirmPrompt confirm,
            final ExportAnnouncer announcer,
            final Clock clock) {
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
        this.proposals = new ExportProposals(projects, executor, project.book()::get);
        this.nameSuggestion = new NameSuggestion(project, messages, setup, this);
        this.statement = new ExportStatement(desk, store, messages, executor);
        this.occupancy = new ExportOccupancy(executor, this::onOccupancyAnswered);
        this.run = new ExportRun(
                exports,
                models,
                settings,
                progress,
                executor,
                activities,
                messages,
                new ExportRecorder(store, clock::instant),
                new ReplaceCheck(confirm));
        this.lastCheck = new LastCheckChoice(stored, executor);
        this.blocker = activities.blocker(ActivityKind.EXPORT);
        wire();
        lastCheck.followBrief(project.brief().get());
        lastCheck.reload();
        log.debug("export view model created, book open {}", project.book().get() != null);
        propose();
        refreshExists();
        refreshRunNote();
    }

    private void wire() {
        blocker.addListener(observed -> refreshRunNote());
        run.running().addListener((observed, was, now) -> recompute());
        run.outcome().addListener((observed, was, now) -> onOutcomeChanged());
        destination.addListener((observed, was, now) -> run.forgetStale("the destination"));
        lastCheck.shown().addListener((observed, was, now) -> run.forgetStale("the last check"));
        sideFiles.addListener((SetChangeListener<SideFile>) change -> run.forgetStale("the side files"));
        statement.figures().addListener((observed, was, now) -> onFigures(now));
        project.book().addListener((observed, was, now) -> onBookChanged(now));
        project.brief().addListener((observed, was, now) -> onBriefChanged(now));
    }

    /** Where the translation is written; empty while no book is open or no target language is chosen. */
    public ReadOnlyStringProperty destination() {
        return destination.getReadOnlyProperty();
    }

    /** Whether a file exists at the destination, as the disk check last answered. */
    public ReadOnlyBooleanProperty destinationExists() {
        return destinationExists.getReadOnlyProperty();
    }

    /** The side files chosen to be written beside the book; glossary only until the person chooses otherwise. */
    public ObservableSet<SideFile> sideFiles() {
        return sideFiles;
    }

    /** Chooses or drops a side file; only a chosen one is asked about before it is replaced. */
    public void setSideFile(final SideFile file, final boolean chosen) {
        Objects.requireNonNull(file, "file");
        log.debug("side file {} chosen: {}", file, chosen);
        if (chosen) {
            sideFiles.add(file);
        } else {
            sideFiles.remove(file);
        }
        refreshExists();
    }

    /** Takes the person's last-check toggle; the saved setting is left as it is. */
    public void setConsistencyPass(final boolean on) {
        lastCheck.choose(on);
    }

    /** Whether the last check runs if the book is exported now: off while the book is partial. */
    public BooleanBinding lastCheckShown() {
        return lastCheck.shown();
    }

    /** Whether the last-check switch is locked off because the book is partial. */
    public BooleanBinding lastCheckLocked() {
        return lastCheck.locked();
    }

    /** The project's counts and last written export, or null while they are not known. */
    public ReadOnlyObjectProperty<@Nullable ExportFigures> figures() {
        return statement.figures();
    }

    /** The running work that rules an export out, or null when none does. */
    public ObjectBinding<@Nullable ActivityKind> blocker() {
        return blocker;
    }

    /** The refusal beside Save as, worded for the person; empty when the destination stands. */
    public ReadOnlyStringProperty refusal() {
        return refusal.getReadOnlyProperty();
    }

    /** The neutral note beside Save as: no destination yet, or the file just exported; empty otherwise. */
    public ReadOnlyStringProperty currentNote() {
        return currentNote.getReadOnlyProperty();
    }

    /** The note that says exporting waits for other work; empty unless such work runs. */
    public ReadOnlyStringProperty runNote() {
        return runNote.getReadOnlyProperty();
    }

    /** Why Export book cannot be pressed now (a translating run, nothing translated); empty when it can. */
    public ReadOnlyStringProperty blockReason() {
        return blockReason.getReadOnlyProperty();
    }

    /** Whether Export book can be used: no competing work, no refusal, a usable destination and no export running. */
    public ReadOnlyBooleanProperty exportAvailable() {
        return exportAvailable.getReadOnlyProperty();
    }

    /** The outcome of the last export, or null before one succeeds and again once the next one starts. */
    public ReadOnlyObjectProperty<@Nullable ExportOutcome> outcome() {
        return run.outcome();
    }

    /** Why the last export wrote nothing, worded for the person; empty otherwise. */
    public ReadOnlyStringProperty failure() {
        return run.failure();
    }

    /** Asks before replacing existing files, then writes the book in the background. */
    public void export() {
        final OpenedBook book = project.book().get();
        final Optional<Path> path = destinationPath();
        if (book == null
                || path.isEmpty()
                || !exportAvailable.get()
                || !blockReason.get().isEmpty()) {
            log.debug("export ignored: a book is open {}, a path {}", book != null, path.isPresent());
            return;
        }
        final Set<SideFile> chosen = Set.copyOf(sideFiles);
        run.start(
                new ExportRequest(
                        book.projectId(),
                        path.get(),
                        false,
                        chosen,
                        lastCheck.shown().get()),
                ExportPathRules.writtenPaths(
                        path.get(), Objects.requireNonNull(book.inspection().format(), "format"), chosen));
    }

    /** Ends the running export's last check and writes the file with the fixes made so far. */
    public void writeNow() {
        run.writeNow();
    }

    ExportActivity.RereadTally rereadTally() {
        return run.rereadTally();
    }

    /** The lines stating what a book written now would contain, as last refreshed by {@link #refreshStatement()}. */
    public ObservableList<String> statement() {
        return statement.lines();
    }

    /** Reads the counts and the last export off the FX thread; they arrive later on the FX thread. */
    public void refreshStatement() {
        final OpenedBook book = project.book().get();
        statement.refresh(book == null ? null : book.projectId());
    }

    /** Looks again for files at the destination, because one can appear or vanish while nothing here changes. */
    public void refresh() {
        log.debug("destination existence refreshed");
        refreshExists();
    }

    /** Takes a path a person chose (blank or unparsable text yields no path); a target change then leaves it alone. */
    public void editDestination(final String text) {
        Objects.requireNonNull(text, "text");
        log.debug("destination edited by hand");
        log.trace("destination edited by hand to '{}'", text);
        destinationEdited = true;
        destination.set(text);
        refreshExists();
    }

    /** Takes a path picked in the file chooser, which counts as the person's choice. */
    public void chooseDestination(final Path chosen) {
        Objects.requireNonNull(chosen, "chosen");
        log.debug("destination chosen in the file chooser: {}", chosen);
        editDestination(chosen.toString());
    }

    /** The destination as a path, or empty when the text is blank or is not a valid path on this system. */
    public Optional<Path> destinationPath() {
        return ExportPathRules.parse(destination.get());
    }

    private void onBookChanged(final @Nullable OpenedBook book) {
        log.debug(
                "the open book changed to {}; the destination is proposed afresh",
                book == null ? null : book.projectId());
        destinationEdited = false;
        run.clear();
        lastCheck.reload();
        propose();
        refreshExists();
        refreshStatement();
    }

    private void onBriefChanged(final @Nullable BookBrief brief) {
        lastCheck.followBrief(brief);
        final String target = brief == null ? null : brief.targetLanguage();
        if (proposals.isProposedFor(target)) {
            return;
        }
        log.debug("the brief's target is now {}, hand-edited {}", target, destinationEdited);
        if (!destinationEdited) {
            propose();
            refreshExists();
        }
    }

    private void propose() {
        final BookBrief brief = project.brief().get();
        proposals.propose(project.book().get(), brief == null ? null : brief.targetLanguage(), this::onProposed);
    }

    private void onProposed(final String path, final boolean labelled) {
        log.debug("destination proposed, labelled {}, hand-edited {}", labelled, destinationEdited);
        if (!labelled) {
            destination.set(path);
        } else if (!destinationEdited && !path.equals(destination.get())) {
            destination.set(path);
            refreshExists();
        }
    }

    private void refreshExists() {
        final Optional<Path> path = destinationPath();
        recompute();
        if (path.isEmpty()) {
            log.debug("existence not asked: no usable path");
            occupancy.clear();
            destinationExists.set(false);
            recompute();
            return;
        }
        occupancy.ask(project.book().get(), path.get(), Set.copyOf(sideFiles));
    }

    private void onOutcomeChanged() {
        final ExportOutcome done = run.outcome().get();
        if (done == null) {
            recompute();
            return;
        }
        statement.written(ExportRecorder.recordOf(done.report(), clock.instant()));
        refreshExists();
        refreshStatement();
        announcer.written(done);
    }

    private void onFigures(final @Nullable ExportFigures figures) {
        log.debug("export figures now known {}, partial {}", figures != null, figures != null && figures.isPartial());
        lastCheck.figures(figures);
        recompute();
    }

    private void onOccupancyAnswered() {
        destinationExists.set(occupancy.isBookTaken());
        recompute();
    }

    private void refreshRunNote() {
        final ActivityKind kind = blocker.get();
        log.debug("export waits for {}", kind);
        runNote.set(ExportRefusals.waitNote(messages, kind));
        recompute();
    }

    private void recompute() {
        final ExportRefusals.Verdict verdict = ExportRefusals.verdict(
                messages, project.book().get(), destinationPath(), occupancy.isBookTaken(), run.justExported());
        log.debug("beside Save as: refusal '{}', note '{}'", verdict.refusal(), verdict.note());
        refusal.set(verdict.refusal());
        currentNote.set(verdict.note());
        final String reason = ExportRefusals.blockReason(
                messages, blocker.get(), statement.figures().get());
        blockReason.set(reason == null ? "" : reason);
        final boolean available = destinationPath().isPresent()
                && !verdict.isBlocking()
                && runNote.get().isEmpty()
                && !run.running().get();
        if (available != exportAvailable.get()) {
            log.debug("export available is now {} while waiting for {}", available, blocker.get());
        }
        exportAvailable.set(available);
    }
}
