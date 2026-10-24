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

    static final String ID_PREFIX = "id:";

    private final Map<Path, Result<ImportedBook>> byPath = new ConcurrentHashMap<>();
    private final ScriptedSegments segments = new ScriptedSegments();
    private final ScriptedListing listing = new ScriptedListing();
    private final ScriptedRoundTrips roundTrips = new ScriptedRoundTrips();
    private final List<Path> imports = new CopyOnWriteArrayList<>();
    private final List<Path> opens = new CopyOnWriteArrayList<>();
    private final Map<Path, Result<ProjectOpening>> openings = new ConcurrentHashMap<>();
    private final List<String> removed = new CopyOnWriteArrayList<>();
    private final List<Boolean> importsOnFxThread = new CopyOnWriteArrayList<>();
    private final List<BookBrief> briefs = new CopyOnWriteArrayList<>();
    private final List<Boolean> briefsOnFxThread = new CopyOnWriteArrayList<>();
    private final List<String> closed = new CopyOnWriteArrayList<>();
    private final List<Boolean> closedOnFxThread = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<String> plans = new CopyOnWriteArrayList<>();
    private final List<Boolean> plansOnFxThread = new CopyOnWriteArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private volatile Result<BookPlan> planAnswer = notScripted();
    private volatile Result<ImportedBook> fallback =
            Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The test scripted no answer for this file."));
    private volatile @Nullable RuntimeException failure;
    private volatile @Nullable CountDownLatch gate;
    private volatile @Nullable CountDownLatch briefGate;
    private volatile @Nullable Result<ProjectChange> briefAnswer;

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

    /** Answers every brief save with {@code answer} (a refused pair, say); null restores "saved". */
    public void onUpdateBrief(final @Nullable Result<ProjectChange> answer) {
        briefAnswer = answer;
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
        roundTrips.answer(answer);
    }

    /** From now on answers every plan with {@code answer}. */
    public void onPlan(final Result<BookPlan> answer) {
        planAnswer = Objects.requireNonNull(answer, "answer");
    }

    /** From now on throws {@code thrown} from every round-trip check, the way a defective adapter would. */
    public void throwingOnRoundTrip(final RuntimeException thrown) {
        roundTrips.throwing(thrown);
    }

    /** From now on blocks each round-trip check until {@link #releaseRoundTrip()}. */
    public void holdRoundTrip() {
        roundTrips.hold();
    }

    /** Lets the held round-trip checks answer. */
    public void releaseRoundTrip() {
        roundTrips.release();
    }

    /** Blocks until a round-trip check has been entered, so a test knows the call is truly in flight. */
    public void awaitRoundTripEntered() throws InterruptedException {
        roundTrips.awaitEntered();
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
        return listing.list();
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

    private final ScriptedProjectEdits edits = new ScriptedProjectEdits();
    /** What rename, translate-into, second-translation and relocate answer, and a record of each. */
    public ScriptedProjectEdits edits() {
        return edits;
    }

    @Override
    public Result<ProjectChange> translateInto(
            final String projectId, final String target, final @Nullable String name) {
        return edits.translateInto(projectId, target, name);
    }

    @Override
    public Result<ProjectChange> makeSecondTranslation(
            final String projectId, final BookBrief brief, final String name) {
        return edits.makeSecondTranslation(projectId, brief, name);
    }

    @Override
    public Result<BookRelocation> relocate(final String projectId, final Path chosen) {
        return edits.relocate(projectId, chosen);
    }

    /** The project this service lists for {@code source}, the one {@link #onDecided} reports. */
    public static Project storedProjectOf(final Path source) {
        return projectOf(ID_PREFIX + source, source);
    }

    /** From now on {@link #list} throws {@code error}, as a JVM out of memory would. */
    public void throwOnList(final Error error) {
        listing.throwOnList(error);
    }

    /** From now on {@link #list} answers exactly these summaries, whatever else was scripted. */
    public void willList(final List<ProjectSummary> summaries) {
        listing.willList(summaries);
    }

    /** From now on {@link #list} reports a project for {@code source} with {@code count} decided segments. */
    public void onDecided(final Path source, final int count) {
        listing.decide(source, count);
    }

    /** From now on answers every {@link #list} with {@code failure}. */
    public void failDecidedWith(final AppError failure) {
        listing.failWith(failure);
    }

    /** How many times {@link #list} was asked. */
    public int listCalls() {
        return listing.calls();
    }

    /** For each {@link #list}, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> listCallsOnFxThread() {
        return listing.onFxThread();
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
        final Result<ProjectChange> scripted = briefAnswer;
        return scripted != null
                ? scripted
                : Result.ok(new ProjectChange.Saved(
                        projectOf(projectId, imports.getLast()).withBrief(brief)));
    }

    @Override
    public Result<ProjectChange> rename(final String projectId, final @Nullable String name) {
        return edits.rename(projectId, name);
    }

    @Override
    public Result<BookPlan> plan(final String projectId) {
        plans.add(Objects.requireNonNull(projectId, "projectId"));
        plansOnFxThread.add(Platform.isFxApplicationThread());
        return planAnswer;
    }

    @Override
    public Result<List<SegmentPreview>> segments(final String projectId, final String unitId) {
        return segments.list(projectId, unitId);
    }

    /** From now on answers a segment listing of {@code unitId} with {@code answer}. */
    public void onSegments(final String unitId, final Result<List<SegmentPreview>> answer) {
        segments.on(unitId, answer);
    }

    /** From now on blocks each listing of {@code unitId} until {@link #releaseSegments}. */
    public void holdSegments(final String unitId) {
        segments.hold(unitId);
    }

    /** Lets the held listings of {@code unitId} answer. */
    public void releaseSegments(final String unitId) {
        segments.release(unitId);
    }

    /** Every segment listing asked for, as {@code projectId/unitId}, in call order. */
    public List<String> segmentCalls() {
        return segments.calls();
    }

    /** For each segment listing, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> segmentCallsOnFxThread() {
        return segments.onFxThread();
    }

    @Override
    public Result<RoundTripReport> roundTrip(final String projectId) {
        return roundTrips.check(projectId);
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
        return roundTrips.projects();
    }

    /** For each round-trip check, in call order, whether it arrived on the FX Application Thread. */
    public List<Boolean> roundTripCallsOnFxThread() {
        return roundTrips.onFxThread();
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
