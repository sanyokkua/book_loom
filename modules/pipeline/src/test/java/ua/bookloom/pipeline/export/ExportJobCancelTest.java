package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.error;
import static ua.bookloom.pipeline.export.ExportJobFixture.hiddenFiles;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * while the consistency pass waits on the model, the book is never written. Nothing is left behind either way.
 */
class ExportJobCancelTest {

    private static final String REPLY = "{\"target\":\"Сем пішла.\"}";

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

        assertThat(error(result).code()).isEqualTo(ErrorCode.cancelled);
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

        assertThat(error(result).code()).isEqualTo(ErrorCode.cancelled);
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve("Book.uk.report.md")).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
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
