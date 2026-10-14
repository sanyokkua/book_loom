package ua.bookloom.ui;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * A hand-written {@link NamesPass} that records every estimate and every run and answers with what a test queued. A run
 * with nothing queued changes nothing and says so; a run can be held, as a slow model would hold it, and then sees a
 * cancellation as the interrupt of the thread that runs it. What the real pass writes to the two repositories is
 * emulated by {@link #writes(Runnable)}, which runs only when the queued answer is a success.
 */
public final class ScriptedNamesPass implements NamesPass {

    private static final long HOLD_SECONDS = 10;

    /** One run the screen asked for. */
    public record Run(
            String projectId, ChatModel model, @Nullable Integer detectedContext) {}

    private final List<String> estimateCalls = new CopyOnWriteArrayList<>();
    private final List<Run> runs = new CopyOnWriteArrayList<>();
    private final Queue<Result<NamesPassReport>> answers = new ConcurrentLinkedQueue<>();
    private final Queue<JobEvent> events = new ConcurrentLinkedQueue<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private volatile Result<NamesEstimate> estimate = Result.ok(new NamesEstimate(4, 2, 4));
    private volatile boolean holding;
    private volatile Runnable duringRun = () -> {};
    private volatile Runnable writes = () -> {};

    /** Sets the estimate every later {@code estimate} call answers with. */
    public void willEstimate(final NamesEstimate value) {
        estimate = Result.ok(value);
    }

    /** Makes every later {@code estimate} call fail with this error. */
    public void willFailToEstimate(final AppError error) {
        estimate = Result.err(error);
    }

    /** Queues the answer of the next run. */
    public void willAnswer(final Result<NamesPassReport> answer) {
        answers.add(answer);
    }

    /** Queues an event the next run reports to its progress receiver before it answers. */
    public void willReport(final JobEvent event) {
        events.add(event);
    }

    /** Makes every later run wait until {@link #release()}, or until its thread is interrupted. */
    public void hold() {
        holding = true;
    }

    /** Lets a held run answer. */
    public void release() {
        holding = false;
        release.countDown();
    }

    /** Sets what runs on the run's thread once it has started and reported its events, to read the app's state. */
    public void duringRun(final Runnable action) {
        duringRun = action;
    }

    /** Sets what runs just before a successful answer, standing for the write of both tables. */
    public void writes(final Runnable action) {
        writes = action;
    }

    /** Every estimate call, as the project id asked about. */
    public List<String> estimateCalls() {
        return List.copyOf(estimateCalls);
    }

    /** Every run, in order. */
    public List<Run> runs() {
        return List.copyOf(runs);
    }

    /** Waits for a run to have started; returns whether one did. */
    public boolean awaitStarted() throws InterruptedException {
        return started.await(HOLD_SECONDS, TimeUnit.SECONDS);
    }

    /** Waits for a held run to see its thread interrupted; returns whether it did. */
    public boolean awaitCancelled() throws InterruptedException {
        return cancelled.await(HOLD_SECONDS, TimeUnit.SECONDS);
    }

    /** An empty report: the pass finished and changed nothing. */
    public static NamesPassReport nothingChanged() {
        return new NamesPassReport(
                EntryChanges.<GlossaryEntry>none(), EntryChanges.<LexiconEntry>none(), 0, Duration.ZERO);
    }

    @Override
    public Result<NamesEstimate> estimate(final String projectId) {
        estimateCalls.add(projectId);
        return estimate;
    }

    @Override
    public Result<NamesPassReport> run(
            final String projectId,
            final ChatModel model,
            final @Nullable Integer detectedContext,
            final Consumer<JobEvent> progress) {
        runs.add(new Run(projectId, model, detectedContext));
        started.countDown();
        JobEvent event = events.poll();
        while (event != null) {
            progress.accept(event);
            event = events.poll();
        }
        duringRun.run();
        if (holding) {
            try {
                release.await(HOLD_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancelled.countDown();
                return Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "The names pass was cancelled."));
            }
        }
        final Result<NamesPassReport> answer = answers.poll();
        if (answer == null) {
            return Result.ok(nothingChanged());
        }
        if (answer.isOk()) {
            writes.run();
        }
        return answer;
    }
}
