package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;

/**
 * A hand-written {@link ProjectService} that answers {@code importBook} with what the test scripted for that path (or
 * an {@code internal} error, or a thrown exception), records every source it was asked to import, every brief it was asked to
 * save and every project it was asked to close together with whether the call arrived on the FX Application Thread,
 * and can hold each import, and each round-trip check, until the test releases it. Round-trip and plan answers are
 * scripted by the test; unscripted, they answer an {@code internal} error.
 */
public final class ScriptedProjectService implements ProjectService {

    private static final long WAIT_SECONDS = 10;

    private final Map<Path, Result<ImportedBook>> byPath = new ConcurrentHashMap<>();
    private final List<Path> imports = new CopyOnWriteArrayList<>();
    private final List<Boolean> importsOnFxThread = new CopyOnWriteArrayList<>();
    private final List<BookBrief> briefs = new CopyOnWriteArrayList<>();
    private final List<Boolean> briefsOnFxThread = new CopyOnWriteArrayList<>();
    private final List<String> closed = new CopyOnWriteArrayList<>();
    private final List<Boolean> closedOnFxThread = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<String> roundTrips = new CopyOnWriteArrayList<>();
    private final List<Boolean> roundTripsOnFxThread = new CopyOnWriteArrayList<>();
    private final List<String> plans = new CopyOnWriteArrayList<>();
    private final List<Boolean> plansOnFxThread = new CopyOnWriteArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch roundTripEntered = new CountDownLatch(1);
    private volatile Result<RoundTripReport> roundTripAnswer = notScripted();
    private volatile Result<BookPlan> planAnswer = notScripted();
    private volatile @Nullable CountDownLatch roundTripGate;
    private volatile @Nullable RuntimeException roundTripFailure;
    private volatile Result<ImportedBook> fallback =
            Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The test scripted no answer for this file."));
    private volatile @Nullable RuntimeException failure;
    private volatile @Nullable CountDownLatch gate;
    private volatile @Nullable CountDownLatch briefGate;

    /** From now on answers an import of {@code source} with {@code answer}; other paths keep the default. */
    public void on(final Path source, final Result<ImportedBook> answer) {
        byPath.put(source, answer);
    }

    /** From now on answers every unscripted path with {@code next}; a previously scripted exception no longer applies. */
    public void respondWith(final Result<ImportedBook> next) {
        failure = null;
        fallback = next;
    }

    /** From now on throws {@code thrown} from every unscripted import, the way a defective adapter would. */
    public void throwing(final RuntimeException thrown) {
        failure = thrown;
    }

    /** From now on blocks each import until {@link #release()}. */
    public void hold() {
        gate = new CountDownLatch(1);
    }

    /** Lets the held imports answer. */
    public void release() {
        final CountDownLatch held = gate;
        if (held != null) {
            held.countDown();
        }
    }

    /** From now on blocks each brief save until {@link #releaseBriefSaves()}, the way a slow disk would. */
    public void holdBriefSaves() {
        briefGate = new CountDownLatch(1);
    }

    /** Lets the held brief saves finish. */
    public void releaseBriefSaves() {
        final CountDownLatch held = briefGate;
        if (held != null) {
            held.countDown();
        }
    }

    /** From now on answers every round-trip check with {@code answer}. */
    public void onRoundTrip(final Result<RoundTripReport> answer) {
        roundTripAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** From now on answers every plan with {@code answer}. */
    public void onPlan(final Result<BookPlan> answer) {
        planAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** From now on throws {@code thrown} from every round-trip check, the way a defective adapter would. */
    public void throwingOnRoundTrip(final RuntimeException thrown) {
        roundTripFailure = thrown;
    }

    /** From now on blocks each round-trip check until {@link #releaseRoundTrip()}. */
    public void holdRoundTrip() {
        roundTripGate = new CountDownLatch(1);
    }

    /** Lets the held round-trip checks answer. */
    public void releaseRoundTrip() {
        final CountDownLatch held = roundTripGate;
        if (held != null) {
            held.countDown();
        }
    }

    /** Blocks until a round-trip check has been entered, so a test knows the call is truly in flight. */
    public void awaitRoundTripEntered() throws InterruptedException {
        if (!roundTripEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the service was never asked for a round-trip check");
        }
    }

    /** Blocks until an import has been entered, so a test knows the call is truly in flight. */
    public void awaitEntered() throws InterruptedException {
        if (!entered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the service was never asked to import a book");
        }
    }

    @Override
    public Result<ImportedBook> importBook(final Path source) {
        final CountDownLatch held = gate;
        imports.add(Objects.requireNonNull(source, "source"));
        events.add("import " + source);
        importsOnFxThread.add(Platform.isFxApplicationThread());
        entered.countDown();
        awaitGate(held);
        final Result<ImportedBook> scripted = byPath.get(source);
        if (scripted != null) {
            return scripted;
        }
        final RuntimeException thrown = failure;
        if (thrown != null) {
            throw thrown;
        }
        return fallback;
    }

    @Override
    public Result<Project> updateBrief(final String projectId, final BookBrief brief) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brief, "brief");
        awaitGate(briefGate);
        briefs.add(brief);
        briefsOnFxThread.add(Platform.isFxApplicationThread());
        return Result.ok(new Project(projectId, imports.getLast(), BookFormat.TXT, "hash", brief));
    }

    @Override
    public Result<BookPlan> plan(final String projectId) {
        plans.add(Objects.requireNonNull(projectId, "projectId"));
        plansOnFxThread.add(Platform.isFxApplicationThread());
        return planAnswer;
    }

    @Override
    public Result<RoundTripReport> roundTrip(final String projectId) {
        final CountDownLatch held = roundTripGate;
        roundTrips.add(Objects.requireNonNull(projectId, "projectId"));
        roundTripsOnFxThread.add(Platform.isFxApplicationThread());
        roundTripEntered.countDown();
        awaitGate(held);
        final RuntimeException thrown = roundTripFailure;
        if (thrown != null) {
            throw thrown;
        }
        return roundTripAnswer;
    }

    @Override
    public Result<Boolean> close(final String projectId) {
        closed.add(Objects.requireNonNull(projectId, "projectId"));
        events.add("close " + projectId);
        closedOnFxThread.add(Platform.isFxApplicationThread());
        return Result.ok(true);
    }

    /** Every import and close in call order, as {@code import <path>} and {@code close <projectId>}. */
    public List<String> events() {
        return List.copyOf(events);
    }

    /** The source of every import, in call order. */
    public List<Path> imports() {
        return List.copyOf(imports);
    }

    /** For each import, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> importCallsOnFxThread() {
        return List.copyOf(importsOnFxThread);
    }

    /** Every brief saved through {@link #updateBrief}, in order. */
    public List<BookBrief> briefs() {
        return List.copyOf(briefs);
    }

    /** For each {@link #updateBrief}, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> briefCallsOnFxThread() {
        return List.copyOf(briefsOnFxThread);
    }

    /** The id of every project a round-trip check was asked for, in call order. */
    public List<String> roundTripProjects() {
        return List.copyOf(roundTrips);
    }

    /** For each round-trip check, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> roundTripCallsOnFxThread() {
        return List.copyOf(roundTripsOnFxThread);
    }

    /** The id of every project a plan was asked for, in call order. */
    public List<String> planProjects() {
        return List.copyOf(plans);
    }

    /** For each plan, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> planCallsOnFxThread() {
        return List.copyOf(plansOnFxThread);
    }

    /** The id of every project {@link #close} was asked to release, in call order. */
    public List<String> closedProjects() {
        return List.copyOf(closed);
    }

    /** For each {@code close}, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> closeCallsOnFxThread() {
        return List.copyOf(closedOnFxThread);
    }

    private static void awaitGate(final @Nullable CountDownLatch held) {
        if (held == null) {
            return;
        }
        try {
            if (!held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the service");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", e);
        }
    }

    private static <T> Result<T> notScripted() {
        return Result.err(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted project service has no answer."));
    }
}
