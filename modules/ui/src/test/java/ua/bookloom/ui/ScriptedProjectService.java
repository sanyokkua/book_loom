package ua.bookloom.ui;

import static ua.bookloom.ui.ScriptedProjects.awaitGate;
import static ua.bookloom.ui.ScriptedProjects.awaitLatch;
import static ua.bookloom.ui.ScriptedProjects.notScripted;
import static ua.bookloom.ui.ScriptedProjects.projectOf;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.BookRelocation;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;

/**
 * A hand-written {@link ProjectService}: {@code create} and {@code open} answer what the test scripted for a path,
 * every call is recorded with whether it arrived on the FX Application Thread, and imports, brief saves and round-trip
 * checks can be held until the test releases them. Unscripted round-trip and plan answers are an {@code internal} error.
 */
public final class ScriptedProjectService implements ProjectService {

    private static final String ID_PREFIX = "id:";

    private final Map<Path, Result<ImportedBook>> byPath = new ConcurrentHashMap<>();
    private final List<Path> imports = new CopyOnWriteArrayList<>();
    private final List<Path> opens = new CopyOnWriteArrayList<>();
    private final Map<Path, Result<ProjectOpening>> openings = new ConcurrentHashMap<>();
    private final List<String> removed = new CopyOnWriteArrayList<>();
    private final AtomicInteger listCalls = new AtomicInteger();
    private final List<Boolean> listOnFxThread = new CopyOnWriteArrayList<>();
    private final Map<Path, Integer> decided = new ConcurrentHashMap<>();
    private volatile @Nullable AppError decidedFailure;
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
    private final Map<String, Result<List<SegmentPreview>>> segmentAnswers = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> segmentGates = new ConcurrentHashMap<>();
    private final List<String> segmentCalls = new CopyOnWriteArrayList<>();
    private final List<Boolean> segmentsOnFxThread = new CopyOnWriteArrayList<>();
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
        awaitLatch(roundTripEntered, "the service was never asked for a round-trip check");
    }

    /** Blocks until an import has been entered, so a test knows the call is truly in flight. */
    public void awaitEntered() throws InterruptedException {
        awaitLatch(entered, "the service was never asked to import a book");
    }

    @Override
    public Result<ImportedBook> create(final Path source) {
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
    public Result<List<ProjectSummary>> list() {
        listCalls.incrementAndGet();
        listOnFxThread.add(Platform.isFxApplicationThread());
        final AppError failed = decidedFailure;
        if (failed != null) {
            return Result.err(failed);
        }
        return Result.ok(decided.entrySet().stream()
                .map(entry ->
                        new ProjectSummary(projectOf(ID_PREFIX + entry.getKey(), entry.getKey()), entry.getValue()))
                .toList());
    }

    @Override
    public Result<ProjectOpening> open(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        final Path source = Path.of(projectId.substring(ID_PREFIX.length()));
        opens.add(source);
        events.add("open " + projectId);
        final Result<ProjectOpening> opening = openings.get(source);
        if (opening != null) {
            return opening;
        }
        final Result<ImportedBook> scripted = byPath.get(source);
        return (scripted != null ? scripted : fallback).map(book -> new ProjectOpening.Opened(book, false));
    }

    /** From now on answers an {@code open} of the project of {@code source} with {@code answer}. */
    public void onOpen(final Path source, final Result<ProjectOpening> answer) {
        openings.put(Objects.requireNonNull(source, "source"), Objects.requireNonNull(answer, "answer"));
    }

    @Override
    public Result<Boolean> remove(final String projectId) {
        removed.add(Objects.requireNonNull(projectId, "projectId"));
        events.add("remove " + projectId);
        return Result.ok(true);
    }

    /** The id of every project {@link #remove} was asked to remove, in call order. */
    public List<String> removedProjects() {
        return List.copyOf(removed);
    }

    @Override
    public Result<ProjectChange> translateInto(
            final String projectId, final String target, final @Nullable String name) {
        return notScripted();
    }

    @Override
    public Result<BookRelocation> relocate(final String projectId, final Path chosen) {
        return notScripted();
    }

    /** The project this service lists for {@code source}, the one {@link #onDecided} reports. */
    public static Project storedProjectOf(final Path source) {
        return projectOf(ID_PREFIX + source, source);
    }

    /** From now on {@link #list} reports a project for {@code source} with {@code count} decided segments. */
    public void onDecided(final Path source, final int count) {
        decided.put(Objects.requireNonNull(source, "source"), count);
    }

    /** From now on answers every {@link #list} with {@code failure}. */
    public void failDecidedWith(final AppError failure) {
        decidedFailure = Objects.requireNonNull(failure, "failure");
    }

    /** How many times {@link #list} was asked. */
    public int listCalls() {
        return listCalls.get();
    }

    /** For each {@link #list}, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> listCallsOnFxThread() {
        return List.copyOf(listOnFxThread);
    }

    /** The source of every {@link #open}, in call order. */
    public List<Path> opens() {
        return List.copyOf(opens);
    }

    @Override
    public Result<ProjectChange> updateBrief(final String projectId, final BookBrief brief) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brief, "brief");
        awaitGate(briefGate);
        briefs.add(brief);
        briefsOnFxThread.add(Platform.isFxApplicationThread());
        return Result.ok(
                new ProjectChange.Saved(projectOf(projectId, imports.getLast()).withBrief(brief)));
    }

    @Override
    public Result<ProjectChange> rename(final String projectId, final @Nullable String name) {
        return notScripted();
    }

    @Override
    public Result<BookPlan> plan(final String projectId) {
        plans.add(Objects.requireNonNull(projectId, "projectId"));
        plansOnFxThread.add(Platform.isFxApplicationThread());
        return planAnswer;
    }

    @Override
    public Result<List<SegmentPreview>> segments(final String projectId, final String unitId) {
        segmentCalls.add(
                Objects.requireNonNull(projectId, "projectId") + "/" + Objects.requireNonNull(unitId, "unitId"));
        segmentsOnFxThread.add(Platform.isFxApplicationThread());
        awaitGate(segmentGates.get(unitId));
        final Result<List<SegmentPreview>> scripted = segmentAnswers.get(unitId);
        return scripted != null ? scripted : notScripted();
    }

    /** From now on answers a segment listing of {@code unitId} with {@code answer}. */
    public void onSegments(final String unitId, final Result<List<SegmentPreview>> answer) {
        segmentAnswers.put(Objects.requireNonNull(unitId, "unitId"), Objects.requireNonNull(answer, "answer"));
    }

    /** From now on blocks each listing of {@code unitId} until {@link #releaseSegments}. */
    public void holdSegments(final String unitId) {
        segmentGates.put(unitId, new CountDownLatch(1));
    }

    /** Lets the held listings of {@code unitId} answer. */
    public void releaseSegments(final String unitId) {
        final CountDownLatch held = segmentGates.get(unitId);
        if (held != null) {
            held.countDown();
        }
    }

    /** Every segment listing asked for, as {@code projectId/unitId}, in call order. */
    public List<String> segmentCalls() {
        return List.copyOf(segmentCalls);
    }

    /** For each segment listing, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> segmentCallsOnFxThread() {
        return List.copyOf(segmentsOnFxThread);
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
}
