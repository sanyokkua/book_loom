package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TimedJobs.timedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/**
 * The person's Retry now during an outage (harness run A, 04:39): it sends the call again at once and restarts the wake
 * schedule, but the outage is the same outage — its start is kept — and a retry the person asked for while the provider
 * was down is not the segment's fault, so it never spends the ten pauses that flag a segment.
 */
class TranslationJobRetryNowTest {

    private static final int RETRIES = 11;

    @TempDir
    private Path tempDir;

    private final ScriptedClock clock = new ScriptedClock();
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_retryNowElevenTimesDuringAnOutage_keepsTheOutageStartAndNeverFlagsTheSegment() {
        final ScriptedChatModel model = replies("ONE.");
        IntStream.range(0, RETRIES).forEach(ignored -> model.answer(error()));
        model.answer(target("TWO.")).answer(target("THREE."));
        final AtomicReference<TranslationJobImpl> holder = new AtomicReference<>();
        final RecoveryTimer retryNowAtEveryWait = delay -> {
            clock.advance(delay);
            Objects.requireNonNull(holder.get(), "job").resume();
            return 0;
        };
        final TranslationJobImpl job = timedJob(
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."), brief("en", "uk")),
                model,
                clock,
                retryNowAtEveryWait,
                (period, tick) -> () -> {});
        holder.set(job);
        job.subscribe(events::add);
        job.recoverWith(() -> Result.err(AppError.of(ErrorCode.unreachable, "Down", "The provider is down.")));

        final JobReport report = report(job.run());

        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(waits()).hasSize(RETRIES);
        assertThat(waits()).extracting(RecoveryWaiting::attempt).containsOnly(1);
        assertThat(waits()).extracting(RecoveryWaiting::downSince).containsOnly(ScriptedClock.START);
    }

    private List<RecoveryWaiting> waits() {
        return events.stream()
                .filter(RecoveryWaiting.class::isInstance)
                .map(RecoveryWaiting.class::cast)
                .toList();
    }

    private static Result<ChatResponse> error() {
        return Result.err(AppError.of(ErrorCode.unreachable, "Provider failure", "The scripted provider failed."));
    }

    private static Result<ChatResponse> target(final String target) {
        return Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(target), FinishReason.STOP));
    }
}
