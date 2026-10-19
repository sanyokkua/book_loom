package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.error;
import static ua.bookloom.pipeline.export.ExportJobFixture.hiddenFiles;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.export.BookExporterTestSupport.RecordingDocumentPort;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * A cancel is honoured as soon as the export can see it: before it starts, no model call and no write happen; raised
 * while the consistency pass waits on the model, the book is never written. Nothing is left behind either way, and the
 * message says how far the export got: nothing written, the book checked but not published, or the book published
 * without its side files.
 */
class ExportJobCancelTest {

    private static final String REPLY = "{\"target\":\"Сем пішла.\"}";
    private static final String NOTHING_WRITTEN = "Nothing was written.";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private RecordingDocumentPort port;
    private String projectId;
    private Path destination;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
        port = new RecordingDocumentPort(fixture.documents());
        projectId = fixture.importBook(
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("Sam went away.")), "en"), "en");
        destination = tempDir.resolve("Book.uk.epub");
        samWaitsOnAKnownGender();
    }

    // A cancel made before run() answers cancelled with no model call, no write and nothing on disk.
    @Test
    void run_cancelledBeforeRun_returnsCancelledWithoutModelCallOrWrite() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo("revision", Result.ok(new ChatResponse(REPLY, FinishReason.STOP)));
        final ExportJob job = ok(fixture.serviceOver(port).newExport(request(), model));
        job.cancel();

        final Result<ExportReport> result = job.run();

        assertThat(error(result))
                .extracting(AppError::code, AppError::title, AppError::message)
                .containsExactly(ErrorCode.cancelled, "Export cancelled", NOTHING_WRITTEN);
        assertThat(model.requests()).isEmpty();
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve("Book.uk.report.md")).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // A cancel raised while the consistency pass's model call is answered stops the export before the book is written.
    @Test
    void run_cancelRaisedDuringConsistencyPass_writesNothing() {
        final AtomicReference<ExportJob> running = new AtomicReference<>();
        final ChatModel cancelling = request -> {
            Objects.requireNonNull(running.get(), "job").cancel();
            return Result.ok(new ChatResponse(REPLY, FinishReason.STOP));
        };
        final ExportJob job = ok(fixture.serviceOver(port).newExport(request(), cancelling));
        running.set(job);

        final Result<ExportReport> result = job.run();

        assertThat(error(result))
                .extracting(AppError::code, AppError::title, AppError::message)
                .containsExactly(ErrorCode.cancelled, "Export cancelled", NOTHING_WRITTEN);
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve("Book.uk.report.md")).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // A cancel must not wait for the provider to answer: the call in flight is interrupted and the export ends.
    @Test
    void cancel_whileAModelCallWaits_interruptsItAndEndsCancelled() {
        final ScriptedChatModel waiting = new ScriptedChatModel().blockNthRequest(1);
        final ExportJob job = ok(fixture.serviceOver(port).newExport(request(), waiting));
        final CompletableFuture<Result<ExportReport>> running = CompletableFuture.supplyAsync(job::run);
        waiting.awaitRequests(1);

        job.cancel();

        assertThat(error(running.orTimeout(5, TimeUnit.SECONDS).join()).code()).isEqualTo(ErrorCode.cancelled);
        assertThat(destination).doesNotExist();
    }

    // A cancel raised once the book is written and checked, before it is moved into place, says exactly that.
    @Test
    void run_cancelRaisedAfterTheBookWasChecked_saysCheckedButNotPublished() {
        final AtomicReference<ExportJob> running = new AtomicReference<>();
        final RecordingDocumentPort cancelling = new RecordingDocumentPort(fixture.documents()) {
            @Override
            public Result<Path> write(
                    final Document document,
                    final Path target,
                    @Nullable final String sourceLanguage,
                    final String targetLanguage) {
                Objects.requireNonNull(running.get(), "job").cancel();
                return super.write(document, target, sourceLanguage, targetLanguage);
            }
        };
        final ExportJob job = ok(fixture.serviceOver(cancelling).newExport(withoutPass(), null));
        running.set(job);

        final Result<ExportReport> result = job.run();

        assertThat(error(result))
                .extracting(AppError::code, AppError::message)
                .containsExactly(
                        ErrorCode.cancelled,
                        "The translated book was checked but not published because the export was cancelled.");
        assertThat(cancelling.writtenDocuments()).hasSize(1);
        assertThat(destination).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // A cancel raised as the book is moved into place leaves the book there and names the side files it stopped.
    @Test
    void run_cancelRaisedAsTheBookIsPublished_keepsTheBookAndSaysTheSideFilesWereNotWritten() {
        final AtomicReference<ExportJob> running = new AtomicReference<>();
        final ExportMoveOperation cancellingMove = (source, target, options) -> {
            Objects.requireNonNull(running.get(), "job").cancel();
            return ExportMoveOperation.nio().move(source, target, options);
        };
        final ExportJob job = ok(fixture.serviceOver(port).newExportWith(withoutPass(), null, cancellingMove));
        running.set(job);

        final Result<ExportReport> result = job.run();

        assertThat(error(result))
                .extracting(AppError::code, AppError::message)
                .containsExactly(
                        ErrorCode.cancelled,
                        "The translated book was written, but the export was cancelled before all its side files were"
                                + " written.");
        assertThat(destination).exists();
        assertThat(tempDir.resolve("Book.uk.report.md")).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    private ExportRequest withoutPass() {
        return new ExportRequest(projectId, destination, false, Set.of(SideFile.QUALITY_REPORT), false);
    }

    private ExportRequest request() {
        return new ExportRequest(projectId, destination, false, Set.of(SideFile.QUALITY_REPORT), true);
    }

    /** Sam's segment is accepted while his gender is unknown, then the gender is set: the pass must call the model. */
    private void samWaitsOnAKnownGender() {
        final String segmentId = fixture.bodyRecords(projectId).getFirst().segmentId();
        fixture.accept(projectId, segmentId, "Сем пішов.");
        final GlossaryEntry unknown = sam(Gender.UNKNOWN);
        ok(fixture.glossary().add(unknown));
        final Document open = Objects.requireNonNull(fixture.openProjects().get(projectId), "open book");
        DeferralRegister.unknownGender(projectId, ExportJobFixture.segment(open, segmentId), List.of(unknown))
                .forEach(deferral -> ok(fixture.deferrals().add(deferral)));
        ok(fixture.glossary().update(sam(Gender.FEMALE)));
    }

    private GlossaryEntry sam(final Gender gender) {
        return new GlossaryEntry(
                GlossaryIds.of(projectId, "Sam"), projectId, "Sam", "Сем", TermType.CHARACTER, gender, false);
    }
}
