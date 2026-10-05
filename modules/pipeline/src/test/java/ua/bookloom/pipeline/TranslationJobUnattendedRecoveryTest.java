package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TimedJobs.timedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.run.RunStores;

/**
 * A run left alone overnight recovers from a provider outage by itself: it waits by the schedule, probes at each wake,
 * resumes when a probe passes, spends a segment's budget only on what is the segment's fault, and leaves a person in
 * charge once they act or once the outage outlasts twelve hours. Every wait is replayed on a scripted clock.
 */
class TranslationJobUnattendedRecoveryTest {

    private static final RunTicks NO_TICKS = (period, tick) -> () -> {};

    @TempDir
    private Path tempDir;

    private final ScriptedClock clock = new ScriptedClock();
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger probes = new AtomicInteger();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Wakes at 0:15, 0:45, 1:45, 3:45, 8:45 and 18:45 find the provider down; the seventh, at 28:45, finds it back.
    @Test
    void run_outageOfTwentyMinutes_resumesByItselfAtTheSeventhWake() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(error(ErrorCode.unreachable))
                .answer(target("TWO."))
                .answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});
        job.recoverWith(downFor(Duration.ofMinutes(20)));

        final JobReport report = report(job.run());

        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(waits()).extracting(RecoveryWaiting::attempt).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(waits().getLast().nextTryAt()).isEqualTo(ScriptedClock.START.plus(Duration.parse("PT28M45S")));
        assertThat(waits()).extracting(RecoveryWaiting::downSince).containsOnly(ScriptedClock.START);
        assertThat(waits())
                .extracting(RecoveryWaiting::probeFailure)
                .containsExactly(
                        null,
                        ErrorCode.unreachable,
                        ErrorCode.unreachable,
                        ErrorCode.unreachable,
                        ErrorCode.unreachable,
                        ErrorCode.unreachable,
                        ErrorCode.unreachable);
        assertThat(probes).hasValue(7);
        assertThat(events).filteredOn(Resumed.class::isInstance).hasSize(1);
    }

    // Past twelve hours down the run stops waking and waits for the person, who here stops it.
    @Test
    void run_outageLongerThanTwelveHours_staysPausedForThePerson() {
        final ScriptedChatModel model = replies("ONE.").answer(error(ErrorCode.unreachable));
        final TranslationJobImpl job = job(model, wake -> {});
        job.recoverWith(downFor(Duration.ofDays(2)));
        job.subscribe(event -> stopOnHold(job, event));

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.CANCELLED);
        final RecoveryWaiting last = waits().getLast();
        assertThat(last.status()).isEqualTo(RecoveryWaiting.Status.GAVE_UP);
        assertThat(last.attempt()).isEqualTo(77);
        assertThat(last.nextTryAt()).isNull();
        assertThat(clock.sinceStart()).isEqualTo(Duration.parse("PT12H8M45S"));
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void run_stopDuringTheWait_cancelsTheRecoveryAtOnce() {
        final ScriptedChatModel model = replies("ONE.").answer(error(ErrorCode.upstream));
        final AtomicReference<TranslationJobImpl> holder = new AtomicReference<>();
        final TranslationJobImpl job =
                job(model, wake -> stopAtThird(Objects.requireNonNull(holder.get(), "job"), wake));
        holder.set(job);
        job.recoverWith(downFor(Duration.ofHours(1)));

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.CANCELLED);
        assertThat(waits()).extracting(RecoveryWaiting::attempt).containsExactly(1, 2, 3);
        assertThat(probes).hasValue(2);
    }

    // A person's Pause during the wait takes the run over: no wake resumes it, only their Resume does.
    @Test
    void run_pauseDuringTheWait_holdsTheRunUntilThePersonResumes() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(error(ErrorCode.unreachable))
                .answer(target("TWO."))
                .answer(target("THREE."));
        final AtomicReference<TranslationJobImpl> holder = new AtomicReference<>();
        final TranslationJobImpl job =
                job(model, wake -> pauseAtSecond(Objects.requireNonNull(holder.get(), "job"), wake));
        holder.set(job);
        job.recoverWith(downFor(Duration.ofHours(1)));
        job.subscribe(event -> resumeOnHold(job, event));

        final JobReport report = report(job.run());

        assertThat(report).extracting(JobReport::end, JobReport::accepted).containsExactly(JobState.COMPLETED, 3);
        assertThat(waits())
                .extracting(RecoveryWaiting::status)
                .containsExactly(
                        RecoveryWaiting.Status.WAITING, RecoveryWaiting.Status.WAITING, RecoveryWaiting.Status.HELD);
        assertThat(probes).hasValue(1);
    }

    // Under the old budget of two pauses the third 429 would have flagged the segment.
    @Test
    void run_rateLimitedThreeTimes_doesNotSpendTheSegmentsBudget() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(error(ErrorCode.rateLimited))
                .answer(error(ErrorCode.rateLimited))
                .answer(error(ErrorCode.rateLimited))
                .answer(target("TWO."))
                .answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});

        final JobReport report = report(job.run());

        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(waits()).extracting(RecoveryWaiting::attempt).containsExactly(1, 2, 3);
    }

    @Test
    void run_timeoutThreeTimes_recoversTwiceThenFlagsAndGoesOn() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(error(ErrorCode.timeout))
                .answer(error(ErrorCode.timeout))
                .answer(error(ErrorCode.timeout))
                .answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.timeout));
        assertThat(pauses()).extracting(Paused::pauses).containsExactly(1, 2);
    }

    @Test
    void run_modelThrowsFourTimes_recoversThreeTimesThenFlagsAndGoesOn() {
        final IllegalStateException fault = new IllegalStateException("scripted model fault");
        final ScriptedChatModel model = replies("ONE.")
                .throwFailure(fault)
                .throwFailure(fault)
                .throwFailure(fault)
                .throwFailure(fault)
                .answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.internal));
        assertThat(pauses()).extracting(Paused::pauses).containsExactly(1, 2, 3);
        assertThat(pauses())
                .extracting(Paused::error)
                .extracting(AppError::cause)
                .containsOnly(fault);
    }

    // A request the provider rejects is rejected again for every segment; waking by itself would flag the whole book.
    @Test
    void run_validationError_waitsForThePersonWithoutWaking() {
        final ScriptedChatModel model = replies("ONE.").answer(error(ErrorCode.validation));
        final AtomicInteger waited = new AtomicInteger();
        final TranslationJobImpl job = job(model, wake -> waited.incrementAndGet());
        job.subscribe(event -> stopOnPause(job, event));

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.CANCELLED);
        assertThat(pauses()).hasSize(1);
        assertThat(waits()).isEmpty();
        assertThat(waited).hasValue(0);
    }

    // LM Studio unloaded the idle model; the first wake's probe finds the server up and the next request loads it.
    @Test
    void run_modelUnloadedOnce_resumesByItselfAtTheFirstWake() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(error(ErrorCode.modelUnavailable))
                .answer(target("TWO."))
                .answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});
        job.recoverWith(downFor(Duration.ZERO));

        final JobReport report = report(job.run());

        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(waits()).extracting(RecoveryWaiting::attempt).containsExactly(1);
        assertThat(clock.sinceStart()).isEqualTo(Duration.ofSeconds(15));
    }

    // The server answers every probe but never loads the model: six wakes, then the person, with nothing flagged.
    @Test
    void run_modelThatNeverLoads_givesUpAfterSixWakesWithoutFlagging() {
        final ScriptedChatModel model = replies("ONE.").answerTimes(7, error(ErrorCode.modelUnavailable));
        final TranslationJobImpl job = job(model, wake -> {});
        job.recoverWith(downFor(Duration.ZERO));
        job.subscribe(event -> stopOnHold(job, event));

        final JobReport report = report(job.run());

        assertThat(report).extracting(JobReport::end, JobReport::flagged).containsExactly(JobState.CANCELLED, 0);
        assertThat(waits())
                .extracting(RecoveryWaiting::status, RecoveryWaiting::attempt)
                .last()
                .isEqualTo(org.assertj.core.groups.Tuple.tuple(RecoveryWaiting.Status.GAVE_UP, 6));
        assertThat(probes).hasValue(6);
        assertThat(pauses()).hasSize(7);
        assertThat(model.requests()).hasSize(8);
    }

    // The model list no longer offers the model: every probe fails, and after six wakes the person loads it.
    @Test
    void run_modelMissingFromEveryProbe_givesUpAfterSixWakes() {
        final ScriptedChatModel model = replies("ONE.").answer(error(ErrorCode.modelUnavailable));
        final TranslationJobImpl job = job(model, wake -> {});
        job.recoverWith(() -> {
            probes.incrementAndGet();
            return Result.err(AppError.of(ErrorCode.modelUnavailable, "Not offered", "The model is not listed."));
        });
        job.subscribe(event -> stopOnHold(job, event));

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.CANCELLED);
        assertThat(waits().getLast())
                .extracting(RecoveryWaiting::status, RecoveryWaiting::attempt, RecoveryWaiting::probeFailure)
                .containsExactly(RecoveryWaiting.Status.GAVE_UP, 6, ErrorCode.modelUnavailable);
        assertThat(clock.sinceStart()).isEqualTo(Duration.parse("PT18M45S"));
        assertThat(model.requests()).hasSize(2);
    }

    // A fault before any segment has nothing to retry or flag: the run ends Failed and says why.
    @Test
    void run_throwsBeforeAnySegment_endsFailedWithAClearMessage() {
        final IllegalStateException fault = new IllegalStateException("glossary store broke");
        final TestProject project = withBrokenGlossary(project(book(), brief("en", "uk")), fault);
        final TranslationJobImpl job = timedJob(project, replies("ONE."), clock, RecoveryTimer.REAL, NO_TICKS);

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.FAILED);
        assertThat(report.error())
                .isNotNull()
                .satisfies(error -> assertThat(error.code()).isEqualTo(ErrorCode.internal))
                .satisfies(error -> assertThat(error.cause()).isSameAs(fault))
                .satisfies(error -> assertThat(error.message()).contains("before it began"));
    }

    private TranslationJobImpl job(final ScriptedChatModel model, final IntConsumer onWait) {
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

    private ua.bookloom.api.pipeline.ProviderProbe downFor(final Duration outage) {
        return () -> {
            probes.incrementAndGet();
            return clock.sinceStart().compareTo(outage) >= 0
                    ? Result.ok(Duration.ofMillis(40))
                    : Result.err(AppError.of(ErrorCode.unreachable, "Down", "The scripted provider is down."));
        };
    }

    private List<RecoveryWaiting> waits() {
        return events.stream()
                .filter(RecoveryWaiting.class::isInstance)
                .map(RecoveryWaiting.class::cast)
                .toList();
    }

    private List<Paused> pauses() {
        return events.stream()
                .filter(Paused.class::isInstance)
                .map(Paused.class::cast)
                .toList();
    }

    private static void stopOnHold(final TranslationJobImpl job, final JobEvent event) {
        if (event instanceof RecoveryWaiting waiting && waiting.status() != RecoveryWaiting.Status.WAITING) {
            job.cancel();
        }
    }

    private static void resumeOnHold(final TranslationJobImpl job, final JobEvent event) {
        if (event instanceof RecoveryWaiting waiting && waiting.status() == RecoveryWaiting.Status.HELD) {
            job.resume();
        }
    }

    private static void stopOnPause(final TranslationJobImpl job, final JobEvent event) {
        if (event instanceof Paused) {
            job.cancel();
        }
    }

    private static void stopAtThird(final TranslationJobImpl job, final int wake) {
        if (wake == 3) {
            job.cancel();
        }
    }

    private static void pauseAtSecond(final TranslationJobImpl job, final int wake) {
        if (wake == 2) {
            job.pause();
        }
    }

    private Path book() {
        return TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
    }

    private static TestProject withBrokenGlossary(final TestProject project, final RuntimeException fault) {
        final GlossaryRepository broken = (GlossaryRepository) Proxy.newProxyInstance(
                GlossaryRepository.class.getClassLoader(),
                new Class<?>[] {GlossaryRepository.class},
                (proxy, method, arguments) -> {
                    throw fault;
                });
        final RunStores stores = project.stores();
        return new TestProject(
                project.id(),
                new RunStores(
                        stores.projects(),
                        stores.segments(),
                        stores.checkpoint(),
                        stores.openProjects(),
                        stores.runs(),
                        broken,
                        stores.tm(),
                        stores.summaries(),
                        stores.lexicon()),
                project.documents(),
                project.deferrals());
    }

    private static Result<ChatResponse> error(final ErrorCode code) {
        return Result.err(AppError.of(code, "Provider failure", "The scripted provider failed."));
    }

    private static Result<ChatResponse> target(final String target) {
        return Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(target), FinishReason.STOP));
    }
}
