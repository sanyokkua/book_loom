package ua.bookloom.pipeline;

import static ua.bookloom.pipeline.TimedJobs.timedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/** The scripted-clock harness shared by the unattended-recovery tests. */
abstract class UnattendedRecoveryHarness {

    static final RunTicks NO_TICKS = (period, tick) -> () -> {};

    @TempDir
    Path tempDir;

    final ScriptedClock clock = new ScriptedClock();
    final List<JobEvent> events = new CopyOnWriteArrayList<>();
    final AtomicInteger probes = new AtomicInteger();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    TranslationJobImpl job(final ScriptedChatModel model, final IntConsumer onWait) {
        final AtomicInteger waitsSoFar = new AtomicInteger();
        final RecoveryTimer timer = delay -> {
            clock.advance(delay);
            onWait.accept(waitsSoFar.incrementAndGet());
            return 0;
        };
        final TranslationJobImpl job = timedJob(project(book(), brief("en", "uk")), model, clock, timer, NO_TICKS);
        job.subscribe(events::add);
        return job;
    }

    ua.bookloom.api.pipeline.ProviderProbe downFor(final Duration outage) {
        return () -> {
            probes.incrementAndGet();
            return clock.sinceStart().compareTo(outage) >= 0
                    ? Result.ok(Duration.ofMillis(40))
                    : Result.err(AppError.of(ErrorCode.unreachable, "Down", "The scripted provider is down."));
        };
    }

    List<RecoveryWaiting> waits() {
        return events.stream()
                .filter(RecoveryWaiting.class::isInstance)
                .map(RecoveryWaiting.class::cast)
                .toList();
    }

    List<Paused> pauses() {
        return events.stream()
                .filter(Paused.class::isInstance)
                .map(Paused.class::cast)
                .toList();
    }

    Path book() {
        return TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
    }

    static Result<ChatResponse> error(final ErrorCode code) {
        return Result.err(AppError.of(code, "Provider failure", "The scripted provider failed."));
    }

    static Result<ChatResponse> target(final String target) {
        return Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(target), FinishReason.STOP));
    }
}
