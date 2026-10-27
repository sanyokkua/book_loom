package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.i18n.Messages;

/**
 * The one source of the run-state row every surface shows: it gathers what the mirror, the view models and the open
 * project know into {@link RunStateInputs} and publishes the {@link RunStateView} worked out from them, together with
 * the chosen AI program and model the navigation footer names and the open project's name.
 *
 * <p>It holds no commands; each surface keeps its own. The row is worked out again on every change, which includes
 * the clocks that tick once a second, so it logs only when the row itself changes. FX thread only.
 */
@Slf4j
@Singleton
public final class RunStateSource {

    private static final int PERCENT = 100;
    private static final String NAME_SEPARATOR = " · ";
    private static final String LANGUAGE_ARROW = " → ";

    private final StateMirror mirror;
    private final TranslatingViewModel translating;
    private final SettingsViewModel settings;
    private final CurrentProject current;
    private final ReviewViewModel review;
    private final Messages messages;
    private final BriefProject briefProject;
    private final ReadOnlyObjectWrapper<RunStateView> view = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper projectOpen = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<ModelChoice> model = new ReadOnlyObjectWrapper<>(ModelChoice.NONE);
    private final ReadOnlyIntegerWrapper decidedSegments = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper totalSegments = new ReadOnlyIntegerWrapper();
    private final ReadOnlyStringWrapper projectName = new ReadOnlyStringWrapper("");
    private final InvalidationListener refresh = observed -> refresh();

    /**
     * Follows every input of the run-state table.
     *
     * @param mirror the run's state, figures, calls and recovery
     * @param translating the run controls on offer, the notice of a refused start and the stored counts
     * @param settings the chosen provider and model
     * @param current the open project
     * @param review the count of flagged segments
     * @param messages the catalogue the time left and the program's name are worded from
     * @param briefProject the open project's stored name, which the title bar shows as the Brief does
     */
    @Inject
    public RunStateSource(
            final StateMirror mirror,
            final TranslatingViewModel translating,
            final SettingsViewModel settings,
            final CurrentProject current,
            final ReviewViewModel review,
            final Messages messages,
            final BriefProject briefProject) {
        this.briefProject = Objects.requireNonNull(briefProject, "briefProject");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.current = Objects.requireNonNull(current, "current");
        this.review = Objects.requireNonNull(review, "review");
        this.messages = Objects.requireNonNull(messages, "messages");
        follow();
        current.book().addListener((observed, was, now) -> translating.refreshPending());
        refresh();
    }

    private void follow() {
        final PendingCount stored = translating.pendingCounts();
        final List<Observable> inputs = List.of(
                mirror.runState(),
                mirror.runFileName(),
                mirror.figures(),
                mirror.live().progress(),
                mirror.live().throughput(),
                mirror.live().waitingCall(),
                mirror.live().calls(),
                mirror.live().suspicious(),
                mirror.review().recovery(),
                mirror.review().providerError(),
                translating.controls(),
                translating.notice(),
                translating.otherWork(),
                stored.count(),
                stored.total(),
                settings.selectedProviderId(),
                settings.model(),
                current.book(),
                current.brief(),
                briefProject.name(),
                review.flaggedCount());
        inputs.forEach(input -> input.addListener(refresh));
    }

    /**
     * The row, worked out again whenever an input changes. It always holds one, so a screen shown before any project
     * (as tests show it) still offers what the row says; the title bar and the banner show only while
     * {@link #projectOpen()}.
     *
     * @return a read-only property that always holds a value; FX thread only
     */
    public ReadOnlyObjectProperty<RunStateView> view() {
        return view.getReadOnlyProperty();
    }

    /**
     * Whether a project is open, or a run exists, so the title bar shows the run and its banner is pinned.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty projectOpen() {
        return projectOpen.getReadOnlyProperty();
    }

    /**
     * The AI program and model chosen, which the chip and the navigation footer both name.
     *
     * @return a read-only property that always holds a value; FX thread only
     */
    public ReadOnlyObjectProperty<ModelChoice> model() {
        return model.getReadOnlyProperty();
    }

    /**
     * The open project's name as the title bar shows it ("Earth Gravity · EN → UK").
     *
     * @return a read-only property, empty while no project is open; FX thread only
     */
    public ReadOnlyStringProperty projectName() {
        return projectName.getReadOnlyProperty();
    }

    /**
     * How many segments are decided: the run's own count while a run exists, else the stored project's.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyIntegerProperty decidedSegments() {
        return decidedSegments.getReadOnlyProperty();
    }

    /**
     * The segments in all: the stored project's before a run, the run's own during one.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyIntegerProperty totalSegments() {
        return totalSegments.getReadOnlyProperty();
    }

    /**
     * How far the run is, for the question asked before it is stopped.
     *
     * @return the segments decided, the segments in all and the segments in flight; never null
     */
    public StopFigures stopFigures() {
        final RunStateInputs in = inputs(model.get());
        return new StopFigures(in.decided(), in.total(), in.inFlight());
    }

    /**
     * The figures the Stop question names.
     *
     * @param decided the segments done and saved
     * @param total the segments in all
     * @param inFlight the segments being translated now, which are translated next time
     */
    public record StopFigures(int decided, int total, int inFlight) {}

    private void refresh() {
        final ModelChoice choice = modelChoice();
        model.set(choice);
        final OpenedBook book = current.book().get();
        final String runFile = mirror.runFileName().get();
        projectName.set(book != null ? titleName(book) : runFile == null ? "" : runFile);
        final RunStateInputs in = inputs(choice);
        decidedSegments.set(in.decided());
        totalSegments.set(in.total());
        setView(RunStateView.of(in));
        final boolean open = book != null || runFile != null;
        if (open != projectOpen.get()) {
            log.debug("project open: {}", open);
            projectOpen.set(open);
        }
    }

    private void setView(final RunStateView next) {
        final @Nullable RunStateView before = view.get();
        if (next.equals(before)) {
            return;
        }
        final @Nullable RunRow was = before == null ? null : before.row();
        if (was != next.row()) {
            log.debug(
                    "run-state row {} -> {} (run state {})",
                    was,
                    next.row(),
                    mirror.runState().get());
        }
        view.set(next);
    }

    private ModelChoice modelChoice() {
        final String providerId = settings.selectedProviderId().get();
        final String provider = providerId.isEmpty() ? "" : settings.providerName(providerId);
        return new ModelChoice(
                provider, settings.model().get().strip(), settings.selection().isPresent());
    }

    private RunStateInputs inputs(final ModelChoice choice) {
        final RunState state = mirror.runState().get();
        final Counts counts = counts(state);
        final @Nullable AppError pausedOn = mirror.review().providerError().get();
        final @Nullable CallSnapshot call = mirror.live().calls().get().current();
        final @Nullable WaitingCall waiting = mirror.live().waitingCall().get();
        return new RunStateInputs(
                state,
                translating.notice().get(),
                mirror.review().recovery().get(),
                pausedOn == null ? null : pausedOn.code(),
                waiting,
                counts.percent(),
                timeLeft(state),
                counts.decided(),
                counts.total(),
                toReview(state) + mirror.live().suspicious().get(),
                inFlight(call, waiting),
                choice,
                translating.controls().get(),
                translating.otherWork().get(),
                span(call, waiting),
                null);
    }

    // The desk reads the stored count, which lags the run's last flush; while the run decides, its own figure counts
    // each
    // flagged segment as it is decided. Once settled the desk's count is the run's figure too (FlaggedCount.show).
    private int toReview(final RunState state) {
        final int desk = review.flaggedCount().get();
        final boolean deciding = state == RunState.RUNNING || state == RunState.PAUSING || state == RunState.STOPPING;
        // Not logged: this is read on every progress tick.
        return deciding ? Math.max(desk, mirror.figures().get().flagged()) : desk;
    }

    /** How far the book is: the decided and all segments, and the share of the work done. */
    private record Counts(int decided, int total, int percent) {}

    // Before any run the stored project's counts tell how far a reopened book is; a run reports its own.
    private Counts counts(final RunState state) {
        if (state == RunState.IDLE) {
            final int total = translating.pendingCounts().total().get();
            final int decided =
                    Math.max(0, total - translating.pendingCounts().count().get());
            return new Counts(decided, total, percentOf(decided, total));
        }
        final RunFigures figures = mirror.figures().get();
        final int total = figures.total();
        final int decided = Math.max(0, total - figures.remaining());
        return new Counts(decided, total, mirror.live().progress().get().percent());
    }

    private static int percentOf(final int decided, final int total) {
        return total == 0 ? 0 : decided * PERCENT / total;
    }

    private @Nullable String timeLeft(final RunState state) {
        final @Nullable Duration left =
                state.hasEnded() ? null : mirror.live().throughput().get().timeLeft();
        return left == null ? null : DurationText.about(messages, left);
    }

    private static int inFlight(final @Nullable CallSnapshot call, final @Nullable WaitingCall waiting) {
        if (call != null && !call.segments().isEmpty()) {
            return call.segments().size();
        }
        return waiting == null ? 0 : waiting.moreSegments() + 1;
    }

    // The segments of the call in flight as a person reads them: the first and the last locator.
    private static @Nullable String span(final @Nullable CallSnapshot call, final @Nullable WaitingCall waiting) {
        if (call == null || call.segments().isEmpty()) {
            return waiting == null || waiting.locator().isEmpty() ? null : waiting.locator();
        }
        final List<CallSegment> segments = call.segments();
        final String first = segments.getFirst().locator();
        final String last = segments.getLast().locator();
        return first.equals(last) ? first : first + "–" + last;
    }

    // The name the project is stored by; the title and the language pair worked out from the book until it is known.
    private String titleName(final OpenedBook book) {
        final String named = briefProject.name().get();
        return named.isEmpty() ? nameOf(book, current.brief().get()) : named;
    }

    // The book's title up to a subtitle, or the file's name without its extension, and the language pair.
    private static String nameOf(final OpenedBook book, final @Nullable BookBrief edited) {
        final BookProfile profile = book.profile();
        final @Nullable String named = profile == null ? null : profile.title();
        final String title = named == null || named.isBlank() ? stem(book.source()) : named.split(":", 2)[0].strip();
        final BookBrief brief = edited == null ? book.brief() : edited;
        final String source = brief.sourceLanguage();
        final String target = brief.targetLanguage();
        if (source == null || target == null) {
            return title;
        }
        return title
                + NAME_SEPARATOR
                + source.toUpperCase(Locale.ROOT)
                + LANGUAGE_ARROW
                + target.toUpperCase(Locale.ROOT);
    }

    private static String stem(final Path source) {
        final Path file = source.getFileName();
        final String name = file == null ? source.toString() : file.toString();
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
