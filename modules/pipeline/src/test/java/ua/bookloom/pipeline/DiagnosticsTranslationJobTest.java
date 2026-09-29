package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.run.RunStores;

/** Verifies MDC correlation and one-time lifecycle logging at the job boundary. */
class DiagnosticsTranslationJobTest {

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger pipelineLogger;

    @BeforeEach
    void attachAppender() {
        pipelineLogger = (Logger) LoggerFactory.getLogger("ua.bookloom.pipeline");
        appender.start();
        pipelineLogger.addAppender(appender);
    }

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
        pipelineLogger.detachAppender(appender);
        appender.stop();
    }

    // Moving MDC setup outside retry attempts would leave a blank or changing segment correlation here.
    @Test
    void run_modelAttempt_setsJobAndSegmentMdcThenCleansBoth() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.");
        final MdcRecordingModel model = new MdcRecordingModel(replies()
                .answer(Result.err(unreachable))
                .answer(Result.ok(new ChatResponse(
                        TranslationJobTestSupport.targetReply("ONE."), ua.bookloom.api.llm.FinishReason.STOP))));
        final TranslationJobImpl translation = markdownJob(model, "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final AtomicReference<MdcObservation> pausedMdc = new AtomicReference<>();
        translation.subscribe(event -> capturePauseMdc(pauses, pausedMdc, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.observed())
                .extracting(MdcObservation::job)
                .allMatch(value -> value != null && !value.isBlank());
        assertThat(model.observed()).extracting(MdcObservation::segment).containsExactly("Book.md:0", "Book.md:0");
        final String jobId = Objects.requireNonNull(model.observed().getFirst().job(), "job MDC");
        assertThat(model.observed()).extracting(MdcObservation::job).containsOnly(jobId);
        assertThat(pausedMdc.get()).isEqualTo(new MdcObservation(jobId, null));
        assertThat(await(workers.submit(DiagnosticsTranslationJobTest::currentMdc)))
                .isEqualTo(new MdcObservation(null, null));
        shutdown(workers);
    }

    // A source path MDC key would collapse these separate jobs into one diagnostic correlation.
    @Test
    void run_distinctJobsForSameSource_assignsDistinctMdcJobs() {
        final Path source = markdown("One.");
        final MdcRecordingModel firstModel = new MdcRecordingModel(replies("ONE."));
        final MdcRecordingModel secondModel = new MdcRecordingModel(replies("ONE."));

        final JobReport first = report(job(source, firstModel).run());
        final JobReport second = report(job(source, secondModel).run());

        assertThat(first.end()).isEqualTo(JobState.COMPLETED);
        assertThat(second.end()).isEqualTo(JobState.COMPLETED);
        final String firstJob =
                Objects.requireNonNull(firstModel.observed().getFirst().job(), "first job MDC");
        final String secondJob =
                Objects.requireNonNull(secondModel.observed().getFirst().job(), "second job MDC");
        assertThat(firstJob).isNotBlank();
        assertThat(secondJob).isNotEqualTo(firstJob);
    }

    // Returning before the run finally block would leak these keys after an internal terminal error.
    @Test
    void run_terminalFailure_cleansMdc() {
        final TranslationJobImpl translation = markdownJob(
                new ScriptedChatModel().throwFailure(new IllegalStateException("diagnostic model throw")), "One.");

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.FAILED);
        assertThat(MDC.get("job")).isNull();
        assertThat(MDC.get("segment")).isNull();
    }

    // Omitting a lifecycle field would make the persistent log insufficient to reconstruct this completed run.
    @Test
    void logging_jobLifecycle_containsRequiredFields() {
        final Path source = markdown("One.");
        final TestProject project = project(source, brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("ONE."));

        translation.run();

        assertLifecycleLogs(project);
    }

    // Logging the caught model throwable again at the job level would create a second boundary cause entry.
    @Test
    void logging_modelThrow_logsCauseExactlyOnce() {
        final IllegalStateException cause = new IllegalStateException("single diagnostic cause");
        final TranslationJobImpl translation = markdownJob(new ScriptedChatModel().throwFailure(cause), "One.");

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.FAILED);
        assertOnlyErrorCarries(cause);
    }

    // An unexpected fault while storing a decision must end as a failed report with one error line and no MDC left.
    @Test
    void logging_boundaryThrow_logsSingleErrorAndCleansMdc() {
        final IllegalStateException cause = new IllegalStateException("checkpoint fault");
        final TestProject project = withCheckpoint(project(markdown("One."), brief("en", "uk")), cause);
        final TranslationJobImpl translation = job(project, replies("ONE."));

        final Result<JobReport> result = translation.run();

        assertThat(report(result))
                .extracting(JobReport::end, JobReport::segments)
                .containsExactly(JobState.FAILED, 1);
        assertOnlyErrorCarries(cause);
        assertThat(MDC.get("job")).isNull();
        assertThat(MDC.get("segment")).isNull();
    }

    // An escaping unmask fault must retain the decisions made before the job boundary catches it.
    @Test
    void run_unmaskThrowAfterPriorDecisions_preservesProgressAndCause() {
        final IllegalStateException cause = new IllegalStateException("unmask fault");
        final TranslationJobImpl translation = job(
                documents -> new ThrowingUnmaskPort(documents, 2, cause),
                markdown("One.\n\nTwo.\n\nThree."),
                replies("ONE.")
                        .answer(TranslationJobTestSupport.cutOffReply())
                        .answer(Result.ok(new ChatResponse(
                                TranslationJobTestSupport.targetReply("THREE."),
                                ua.bookloom.api.llm.FinishReason.STOP))));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final JobReport result = report(translation.run());

        assertPreservedUnmaskFault(result, events, cause);
    }

    // A recoverable failure pause is visible at WARN without duplicating the throwable cause.
    @Test
    void logging_errorPause_recordsRecoveryDetailsWithoutCause() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.");
        final TranslationJobImpl translation = markdownJob(replies().answer(Result.err(unreachable)), "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);

        assertRecoveryWarning();
        translation.cancel();
        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        shutdown(workers);
    }

    // Removing either conversion pattern token would make correlation unavailable in the trace acceptance log.
    @Test
    void logbackTestPattern_containsJobAndSegmentMdc() throws Exception {
        final String pattern = Files.readString(Path.of("src/test/resources/logback-test.xml"));

        assertThat(pattern).contains("%X{job}", "%X{segment}");
    }

    private TranslationJobImpl markdownJob(final ChatModel model, final String content) {
        return job(markdown(content), model);
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static TestProject withCheckpoint(final TestProject project, final RuntimeException failure) {
        final CheckpointPort throwing = commit -> {
            throw failure;
        };
        final RunStores stores = project.stores();
        return new TestProject(
                project.id(),
                new RunStores(
                        stores.projects(),
                        stores.segments(),
                        throwing,
                        stores.openProjects(),
                        stores.runs(),
                        stores.glossary(),
                        stores.tm()),
                project.documents());
    }

    private void assertLifecycleLogs(final TestProject project) {
        final ILoggingEvent start = onlyEvent(Level.INFO, "Translation job started");
        final ILoggingEvent end = onlyEvent(Level.INFO, "Translation job ended");
        assertThat(start.getFormattedMessage())
                .contains(
                        "project=" + project.id(),
                        "format=MARKDOWN",
                        "sourceLanguage=en",
                        "targetLanguage=uk",
                        "mode=UNATTENDED",
                        "dial=FAST",
                        "contextSize=8192",
                        "pausePoints=[]",
                        "segments=1",
                        "sections=1");
        assertThat(end.getFormattedMessage()).contains("state=COMPLETED", "segments=1", "accepted=1", "flagged=0");
        assertThat(start.getMDCPropertyMap().get("job")).isNotBlank();
        assertThat(end.getMDCPropertyMap().get("job"))
                .isEqualTo(start.getMDCPropertyMap().get("job"));
    }

    private void assertOnlyErrorCarries(final Throwable cause) {
        final ILoggingEvent error = onlyEvent(Level.ERROR, "");
        assertThat(error.getThrowableProxy()).isInstanceOf(ThrowableProxy.class);
        assertThat(((ThrowableProxy) error.getThrowableProxy()).getThrowable()).isSameAs(cause);
        assertThat(appender.list)
                .filteredOn(event -> event.getFormattedMessage().contains("Translation job ended failed"))
                .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    private void assertPreservedUnmaskFault(
            final JobReport result, final List<JobEvent> events, final IllegalStateException cause) {
        assertThat(result)
                .extracting(JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.FAILED, 3, 1, 1);
        assertThat(result.flaggedSegments())
                .singleElement()
                .extracting(FlaggedSegment::segmentId, FlaggedSegment::reason)
                .containsExactly("Book.md:1", ErrorCode.validation);
        assertThat(result.error()).extracting(AppError::cause).isSameAs(cause);
        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly(
                        "StageStarted",
                        "StageStarted",
                        "ModelCallStarted",
                        "SegmentDecided",
                        "ModelCallStarted",
                        "SegmentDecided",
                        "ModelCallStarted",
                        "Finished");
        assertThat(((Finished) events.getLast()).report()).isEqualTo(result);
        assertOnlyErrorCarries(cause);
    }

    private void assertRecoveryWarning() {
        final ILoggingEvent warning = onlyEvent(Level.WARN, "Pausing translation job after recoverable error");
        assertThat(warning.getFormattedMessage())
                .contains(
                        "stage=TRANSLATE",
                        "reason=ON_ERROR",
                        "errorCode=unreachable",
                        "accepted=0",
                        "flagged=0",
                        "pending=1");
        assertThat(warning.getThrowableProxy()).isNull();
    }

    private ILoggingEvent onlyEvent(final Level level, final String message) {
        final List<ILoggingEvent> events = appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .filter(event -> event.getFormattedMessage().contains(message))
                .toList();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private static void capturePauseMdc(
            final LinkedBlockingQueue<Paused> pauses,
            final AtomicReference<MdcObservation> observed,
            final JobEvent event) {
        if (event instanceof Paused) {
            observed.set(currentMdc());
        }
        capturePaused(pauses, event);
    }

    private static MdcObservation currentMdc() {
        return new MdcObservation(MDC.get("job"), MDC.get("segment"));
    }

    private record MdcObservation(
            @Nullable String job, @Nullable String segment) {}

    private static final class ThrowingUnmaskPort extends TestDocuments.ForwardingPort {

        private final int failingCall;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();

        ThrowingUnmaskPort(final DocumentPort delegate, final int failingCall, final RuntimeException failure) {
            super(delegate);
            this.failingCall = failingCall;
            this.failure = failure;
        }

        @Override
        public Result<String> unmask(final BookFormat format, final Segment segment, final String translatedMasked) {
            if (calls.incrementAndGet() == failingCall) {
                throw failure;
            }
            return super.unmask(format, segment, translatedMasked);
        }
    }

    private static final class MdcRecordingModel implements ChatModel {

        private final ChatModel delegate;
        private final List<MdcObservation> observed = new CopyOnWriteArrayList<>();

        MdcRecordingModel(final ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            observed.add(new MdcObservation(MDC.get("job"), MDC.get("segment")));
            return delegate.chat(request);
        }

        List<MdcObservation> observed() {
            return List.copyOf(observed);
        }
    }
}
