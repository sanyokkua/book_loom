package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.documents;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.StageStarted;

/** Covers startup refusals, source release, terminal controls, and one-run claiming. */
class TranslationJobLifecycleTest {

    @TempDir
    private Path tempDir;

    // Removing the successful terminal branch would make this report or exported book differ.
    @Test
    void run_threeAcceptedSegments_completesAndWritesReport() {
        final Path source = markdown("One.\n\nTwo.\n\nThree.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final TranslationJobImpl translation = job(documents(), source, destination, replies("ONE.", "TWO.", "THREE."));

        final Result<JobReport> result = translation.run();

        assertThat(result.isOk()).isTrue();
        assertThat(report(result))
                .extracting(
                        JobReport::end,
                        JobReport::format,
                        JobReport::segments,
                        JobReport::accepted,
                        JobReport::flagged,
                        JobReport::written,
                        JobReport::error)
                .containsExactly(JobState.COMPLETED, BookFormat.MARKDOWN, 3, 3, 0, destination, null);
        assertThat(report(result).flaggedSegments()).isEmpty();
        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(Files.exists(destination)).isTrue();
    }

    // Dropping accumulated decisions before export would publish the accepted source text instead.
    @Test
    void run_acceptedAndFlaggedMarkdown_writesTranslatedAndVerbatimParagraphs() throws Exception {
        final Path source = markdown("He opened the *old* door.\n\nKeep *this* paragraph.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final AppError invalid = AppError.of(ErrorCode.validation, "Invalid", "The reply is invalid.");
        final ScriptedChatModel model =
                replies("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.").answer(Result.err(invalid));

        final JobReport completed =
                report(job(documents(), source, destination, model).run());

        assertThat(completed)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 1, 1);
        assertThat(Files.readString(destination)).isEqualTo("HE OPENED THE *OLD* DOOR.\n\nKeep *this* paragraph.");
    }

    // Skipping the startup destination refusal would overwrite this sentinel and call the model.
    @Test
    void run_existingDestinationWithoutOverwrite_returnsStartupError() throws Exception {
        final Path source = markdown("One.");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "KEEP");
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(documents(), source, destination, model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> result = translation.run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
        assertThat(Files.readString(destination)).isEqualTo("KEEP");
        assertThat(events).isEmpty();
    }

    // Replacing the source-open error would hide the port's startup refusal behind a report.
    @Test
    void run_sourceOpenFailure_returnsStartupError() {
        final Path source = tempDir.resolve("Book.md");
        final AppError failure = AppError.of(ErrorCode.validation, "Unreadable", "The source cannot open.");
        final ScriptedChatModel model = replies("ONE.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final TranslationJobImpl translation =
                job(new BookExporterTestSupport.OpenFailurePort(documents(), failure), source, destination, model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> result = translation.run();

        assertThat(result.error()).isSameAs(failure);
        assertThat(model.requests()).isEmpty();
        assertThat(events).isEmpty();
        assertThat(Files.exists(destination)).isFalse();
    }

    // Moving source close after inference would make this model observe an open source snapshot.
    @Test
    void run_sourceSnapshot_closesBeforeFirstModelCall() {
        final Path source = markdown("One.");
        final BookExporterTestSupport.RecordingDocumentPort recording =
                new BookExporterTestSupport.RecordingDocumentPort(documents());
        final AtomicBoolean closedBeforeChat = new AtomicBoolean();
        final ChatModel model = observingModel(closedBeforeChat, recording, replies("ONE."));

        final Result<JobReport> result =
                job(recording, source, tempDir.resolve("Book.uk.md"), model).run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(closedBeforeChat).isTrue();
    }

    // Releasing a parsed source failure must remain a terminal report because counts are known.
    @Test
    void run_sourceReleaseFailure_returnsFailedReportWithKnownCounts() {
        final Path source = markdown("One.\n\nTwo.");
        final AppError failure = AppError.of(ErrorCode.internal, "Close failed", "The source could not be released.");
        final ScriptedChatModel model = replies("ONE.", "TWO.");
        final TranslationJobImpl translation = job(
                new BookExporterTestSupport.SingleCloseFailurePort(documents(), 0, failure),
                source,
                tempDir.resolve("Book.uk.md"),
                model);

        final Result<JobReport> result = translation.run();

        assertThat(report(result))
                .extracting(
                        JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged, JobReport::error)
                .containsExactly(JobState.FAILED, 2, 0, 0, failure);
        assertThat(model.requests()).isEmpty();
    }

    // Dropping the claimed flag would run a completed job a second time and repeat its model call.
    @Test
    void run_afterCompletion_returnsValidationWithoutNewWork() {
        final Path source = markdown("One.");
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(documents(), source, tempDir.resolve("Book.uk.md"), model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> first = translation.run();
        final int eventCount = events.size();

        final Result<JobReport> second = translation.run();

        assertThat(report(first).end()).isEqualTo(JobState.COMPLETED);
        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(second.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).hasSize(1);
        assertThat(events).hasSize(eventCount);
    }

    // Publishing the claim after StageStarted would permit this recursive call to start duplicate work.
    @Test
    void run_calledRecursivelyFromStageCallback_returnsValidation() {
        final Path source = markdown("One.");
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(documents(), source, tempDir.resolve("Book.uk.md"), model);
        final AtomicReference<Result<JobReport>> nested = new AtomicReference<>();
        translation.subscribe(event -> recordRecursiveRun(translation, nested, event));

        final Result<JobReport> outer = translation.run();

        assertThat(report(outer).end()).isEqualTo(JobState.COMPLETED);
        assertThat(nested.get())
                .extracting(Result::error)
                .extracting(AppError::code)
                .isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).hasSize(1);
    }

    // Reading the source before checking cancellation would violate this zero-work cancellation report.
    @Test
    void cancel_beforeRun_finishesCancelledWithoutOpening() {
        final Path source = markdown("One.");
        final BookExporterTestSupport.RecordingDocumentPort recording =
                new BookExporterTestSupport.RecordingDocumentPort(documents());
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(recording, source, tempDir.resolve("Book.uk.md"), model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);
        translation.cancel();

        assertThat(translation.state()).isEqualTo(JobState.CANCELLED);

        final Result<JobReport> result = translation.run();

        assertThat(translation.state()).isEqualTo(JobState.CANCELLED);
        assertThat(report(result))
                .extracting(
                        JobReport::end,
                        JobReport::segments,
                        JobReport::accepted,
                        JobReport::flagged,
                        JobReport::written,
                        JobReport::error)
                .containsExactly(JobState.CANCELLED, 0, 0, 0, null, null);
        assertThat(recording.openedDocuments()).isEmpty();
        assertThat(model.requests()).isEmpty();
        assertThat(events).hasSize(1).allMatch(Finished.class::isInstance);
        assertThat(((Finished) events.getFirst()).report()).isEqualTo(report(result));
    }

    // Replaying stored events or mutating a terminal state would make this late listener observe work.
    @Test
    void controls_terminalJob_areNoOpsAndSubscriptionsDoNotReplay() {
        final TranslationJobImpl translation =
                job(documents(), markdown("One."), tempDir.resolve("Book.uk.md"), replies("ONE."));
        translation.run();
        final List<JobEvent> lateEvents = new ArrayList<>();
        translation.subscribe(lateEvents::add);

        translation.pause();
        translation.resume();
        translation.cancel();
        translation.pauseAt(java.util.Set.of());

        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(lateEvents).isEmpty();
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static ChatModel observingModel(
            final AtomicBoolean closedBeforeChat,
            final BookExporterTestSupport.RecordingDocumentPort recording,
            final ScriptedChatModel delegate) {
        return request -> observeAndReply(closedBeforeChat, recording, delegate, request);
    }

    private static Result<ChatResponse> observeAndReply(
            final AtomicBoolean closedBeforeChat,
            final BookExporterTestSupport.RecordingDocumentPort recording,
            final ScriptedChatModel delegate,
            final ChatRequest request) {
        closedBeforeChat.set(recording.closedDocuments().size() == 1);
        return delegate.chat(request);
    }

    private static void recordRecursiveRun(
            final TranslationJobImpl translation,
            final AtomicReference<Result<JobReport>> nested,
            final JobEvent event) {
        if (event instanceof StageStarted started && started.stage() == JobStage.TRANSLATE) {
            nested.set(translation.run());
        }
    }
}
