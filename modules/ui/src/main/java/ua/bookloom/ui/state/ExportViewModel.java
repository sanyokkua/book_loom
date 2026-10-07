package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
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
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.util.paths.DestinationPath;

/**
 * Where the translation is written and whether an existing file may be replaced, for the open book.
 *
 * <p>A singleton, like {@link CurrentProject}, because the screens are rebuilt on every visit while the choices must
 * survive them. It is first constructed when a screen first needs it, which can be long after the book was opened, so
 * it proposes a destination for a book that is already open. The proposed file name is never built here:
 * {@link DestinationPath} owns that rule, so the command line and the window cannot drift apart. The proposal follows
 * the brief's target language until the person has typed or picked a path of their own.
 *
 * <p>Looking for a file at the destination is I/O, so it runs on the background executor and its answer is published
 * back with {@code Platform.runLater}. Each question bumps a counter and an answer is published only if the counter
 * is what it was when the question was asked, so a slow answer about a path typed earlier can never overwrite a newer
 * one. The same question covers each chosen side file's path, because the export refuses any of them being taken.
 * While an answer is in flight {@link #destinationExists()} keeps its previous value rather than flickering through a
 * guessed one. Everything else here is touched on the FX thread only.
 */
@Slf4j
@Singleton
public final class ExportViewModel {

    private final CurrentProject project;
    private final Messages messages;
    private final ObservableSet<SideFile> sideFiles = FXCollections.observableSet(EnumSet.of(SideFile.GLOSSARY_CSV));
    private final ReadOnlyBooleanWrapper consistencyPass = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper refusal = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper currentNote = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper runNote = new ReadOnlyStringWrapper("");
    private final ObjectBinding<@Nullable ActivityKind> blocker;
    private final ReadOnlyBooleanWrapper exportAvailable = new ReadOnlyBooleanWrapper(false);
    private final ExportStatement statement;
    private final ExportOccupancy occupancy;
    private final ExportRun run;
    public final NameSuggestion nameSuggestion; // the model's proposal of the translated book's file name
    // The dial the consistency switch last followed, so an unrelated brief change leaves the person's choice alone.
    private @Nullable QualityDial followedDial;
    private final ReadOnlyStringWrapper destination = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper overwrite = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper destinationExists = new ReadOnlyBooleanWrapper(false);
    // Whether the person, and not a proposal, put the text in the destination. FX thread only.
    private boolean destinationEdited;
    // The target the last proposal was made for, so a brief change that leaves it alone proposes nothing.
    private @Nullable String proposedFor;

    /**
     * Follows the open book and its brief, and proposes a destination for a book that is already open.
     *
     * @param project the holder of the open book, which outlives this view model
     * @param desk the review desk whose counts the partial-export statement states
     * @param messages the catalogue the refusals and the statement are worded from
     * @param exports the port an export is asked of
     * @param models the port the consistency pass's model is created through
     * @param settings the provider settings, whose selection names that model
     * @param progress the workflow marks, where a written book marks the export step
     * @param executor the daemon executor the existence check and the export run on, never the FX thread
     * @param activities the model work under way; an export waits for any that would compete with it for the model
     * @param setup the model's setup proposals, or null for a window that offers none
     */
    @Inject
    public ExportViewModel(
            final CurrentProject project,
            final ReviewDesk desk,
            final Messages messages,
            final ExportService exports,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            final WorkflowProgress progress,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities,
            final @Nullable SetupHelper setup) {
        this.project = Objects.requireNonNull(project, "project");
        this.nameSuggestion = new NameSuggestion(project, messages, setup, this);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.statement = new ExportStatement(desk, messages, executor);
        this.occupancy = new ExportOccupancy(executor, this::onOccupancyAnswered);
        this.run = new ExportRun(exports, models, settings, progress, executor, activities, messages);
        this.blocker = activities.blocker(ActivityKind.EXPORT);
        blocker.addListener(observed -> refreshRunNote());
        run.running().addListener((observed, was, now) -> recompute());
        run.outcome().addListener((observed, was, now) -> onOutcomeChanged());
        destination.addListener((observed, was, now) -> run.forgetStale("the destination"));
        overwrite.addListener((observed, was, now) -> run.forgetStale("replace"));
        consistencyPass.addListener((observed, was, now) -> run.forgetStale("the consistency pass"));
        sideFiles.addListener((SetChangeListener<SideFile>) change -> run.forgetStale("the side files"));
        project.book().addListener((observed, was, now) -> onBookChanged(now));
        project.brief().addListener((observed, was, now) -> onBriefChanged(now));
        followDial(project.brief().get());
        log.debug(
                "export view model created, a book is already open: {}",
                project.book().get() != null);
        propose();
        refreshExists();
        refreshRunNote();
    }

    /**
     * Where the translation is written, as the person sees and edits it.
     *
     * @return a read-only property holding the path text, empty while no book is open or no target language is chosen,
     *     or after it was cleared by hand
     */
    public ReadOnlyStringProperty destination() {
        return destination.getReadOnlyProperty();
    }

    /**
     * Whether an existing destination file may be replaced.
     *
     * @return a read-only property; off until a person allows it
     */
    public ReadOnlyBooleanProperty overwrite() {
        return overwrite.getReadOnlyProperty();
    }

    /**
     * Whether the run would be refused for an occupied destination, so a screen can say so before it starts.
     *
     * @return a read-only property, true only while a file exists at the destination and replacing is not allowed
     */
    public ReadOnlyBooleanProperty destinationExists() {
        return destinationExists.getReadOnlyProperty();
    }

    /** The side files chosen to be written beside the book; glossary only until the person chooses otherwise. */
    public ObservableSet<SideFile> sideFiles() {
        return sideFiles;
    }

    /** Chooses or drops a side file; its path only refuses the export while it is chosen. */
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

    /** Whether the final consistency pass runs; on exactly when the brief's dial is {@code MAX}, until changed. */
    public ReadOnlyBooleanProperty consistencyPass() {
        return consistencyPass.getReadOnlyProperty();
    }

    public void setConsistencyPass(final boolean on) {
        log.debug("consistency pass is now {}", on);
        consistencyPass.set(on);
    }

    /** The refusal beside Save to, worded for the person; empty when the destination stands. */
    public ReadOnlyStringProperty refusal() {
        return refusal.getReadOnlyProperty();
    }

    /** The neutral note beside Save to when it names the file just exported; empty otherwise. */
    public ReadOnlyStringProperty currentNote() {
        return currentNote.getReadOnlyProperty();
    }

    /** The note that says exporting waits for the run; empty unless a run is running, pausing or stopping. */
    public ReadOnlyStringProperty runNote() {
        return runNote.getReadOnlyProperty();
    }

    /** Whether Export book can be used: no translating run, no refusal, and a usable destination path. */
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

    /**
     * Writes the book to the destination with the chosen side files, in the background. Does nothing while Export book
     * is unavailable. The result arrives as {@link #outcome()} or {@link #failure()}.
     */
    public void export() {
        final OpenedBook book = project.book().get();
        final Optional<Path> path = destinationPath();
        if (book == null || path.isEmpty() || !exportAvailable.get()) {
            log.debug("export ignored: a book is open {}, a path {}", book != null, path.isPresent());
            return;
        }
        run.start(new ExportRequest(
                book.projectId(), path.get(), overwrite.get(), Set.copyOf(sideFiles), consistencyPass.get()));
    }

    /** The lines stating what a book written now would contain, as last refreshed by {@link #refreshStatement()}. */
    public ObservableList<String> statement() {
        return statement.lines();
    }

    /** Reads the review desk's counts off the FX thread and words them; the lines arrive later on the FX thread. */
    public void refreshStatement() {
        final OpenedBook book = project.book().get();
        statement.refresh(book == null ? null : book.projectId());
    }

    /**
     * Looks again for a file at the destination, because one can appear or vanish while nothing here changes and the
     * warning is only worth showing if it is current. The answer arrives later on the FX thread.
     */
    public void refresh() {
        log.debug("destination existence refreshed");
        refreshExists();
    }

    /**
     * Takes a path a person typed. From here on a target change leaves it alone.
     *
     * @param text the path as typed; blank or unparsable text is kept as typed and simply yields no path
     */
    public void editDestination(final String text) {
        Objects.requireNonNull(text, "text");
        log.debug("destination edited by hand");
        log.trace("destination edited by hand to '{}'", text);
        destinationEdited = true;
        destination.set(text);
        refreshExists();
    }

    /**
     * Takes a path picked in the file chooser, which counts as a choice like typing one does.
     *
     * @param chosen the file the person picked
     */
    public void chooseDestination(final Path chosen) {
        Objects.requireNonNull(chosen, "chosen");
        log.debug("destination chosen in the file chooser: {}", chosen);
        editDestination(chosen.toString());
    }

    /**
     * Allows or forbids replacing a file that already exists at the destination.
     *
     * @param allowed whether replacing is allowed
     */
    public void setOverwrite(final boolean allowed) {
        log.debug("overwrite is now {}", allowed);
        overwrite.set(allowed);
        refreshExists();
    }

    /**
     * The destination as a path.
     *
     * @return the parsed destination, or empty when the text is blank or is not a valid path on this system
     */
    public Optional<Path> destinationPath() {
        return ExportPathRules.parse(destination.get());
    }

    private void onBookChanged(final @Nullable OpenedBook book) {
        log.debug(
                "the open book changed to {}; the destination is proposed afresh",
                book == null ? null : book.projectId());
        destinationEdited = false;
        run.clear();
        overwrite.set(false);
        propose();
        refreshExists();
        refreshStatement();
    }

    private void onBriefChanged(final @Nullable BookBrief brief) {
        followDial(brief);
        final String target = brief == null ? null : brief.targetLanguage();
        if (Objects.equals(target, proposedFor)) {
            return;
        }
        log.debug("the brief's target is now {}, destination hand-edited: {}", target, destinationEdited);
        if (!destinationEdited) {
            propose();
            refreshExists();
        }
    }

    private void propose() {
        final OpenedBook book = project.book().get();
        final BookBrief brief = project.brief().get();
        final String target = brief == null ? null : brief.targetLanguage();
        proposedFor = target;
        if (book == null || target == null) {
            log.debug("no proposal: a book is open {}, a target is chosen {}", book != null, target != null);
            destination.set("");
            return;
        }
        final BookFormat format = Objects.requireNonNull(book.inspection().format(), "format");
        final Path proposed = DestinationPath.destinationFor(book.source(), format, target);
        log.debug("proposing destination {} for project {} ({})", proposed, book.projectId(), format);
        destination.set(proposed.toString());
    }

    private void refreshExists() {
        final Optional<Path> path = destinationPath();
        recompute();
        if (overwrite.get() || path.isEmpty()) {
            log.debug("existence not asked: overwrite allowed {}, usable path {}", overwrite.get(), path.isPresent());
            occupancy.clear();
            destinationExists.set(false);
            recompute();
            return;
        }
        final OpenedBook book = project.book().get();
        occupancy.ask(
                book == null
                        ? List.of(path.get())
                        : ExportPathRules.writtenPaths(
                                path.get(),
                                Objects.requireNonNull(book.inspection().format(), "format"),
                                Set.copyOf(sideFiles)));
    }

    private void onOutcomeChanged() {
        if (run.outcome().get() != null) {
            refreshExists();
        } else {
            recompute();
        }
    }

    private void onOccupancyAnswered() {
        destinationExists.set(occupancy.isBookTaken());
        recompute();
    }

    private void followDial(final @Nullable BookBrief brief) {
        final QualityDial dial = brief == null ? null : brief.dial();
        if (dial != followedDial) {
            followedDial = dial;
            log.debug("the brief's dial is now {}; the consistency pass follows it", dial);
            consistencyPass.set(dial == QualityDial.MAX);
        }
    }

    private void refreshRunNote() {
        final ActivityKind kind = blocker.get();
        log.debug("export waits for {}", kind);
        runNote.set(ExportRefusals.waitNote(messages, kind));
        recompute();
    }

    private void recompute() {
        final ExportRefusals.Verdict verdict = ExportRefusals.verdict(
                messages, project.book().get(), destinationPath(), occupancy.occupied(), run.justExported());
        log.debug("beside Save to: refusal '{}', note '{}'", verdict.refusal(), verdict.note());
        refusal.set(verdict.refusal());
        currentNote.set(verdict.note());
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
