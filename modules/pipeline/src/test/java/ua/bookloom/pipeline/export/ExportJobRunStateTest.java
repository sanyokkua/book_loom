package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.MAPPER;
import static ua.bookloom.pipeline.export.ExportJobFixture.error;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.request;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.export.BookExporterTestSupport.BlockingClosePort;

/** The export is the only writer and never reads or changes a run's control state. */
class ExportJobRunStateTest {

    private static final long WAIT_SECONDS = 5;

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private final ExecutorService workers = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    @AfterEach
    void tearDown() {
        workers.shutdownNow();
    }

    // A run paused on a provider error keeps its state and counts when an export of its project fails.
    @Test
    void run_failingExportOfPausedRun_leavesTheRunPausedWithItsCounts() {
        final String id = fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), "en");
        final RunRecord paused =
                new RunRecord("run-1", id, Instant.parse("2026-01-01T00:00:00Z"), null, JobState.PAUSED, 5, 1);
        ok(fixture.runs().save(paused));
        final Path destination = TestBooks.markdown(tempDir.resolve("Book.uk.md"), "Existing.");

        final Result<ExportReport> result = fixture.export(request(id, destination, false));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(ok(fixture.runs().latest(id))).contains(paused);
    }

    // A pause asked of a finished run while its book is being exported changes neither the run nor the export.
    @Test
    void run_pauseRequestedOnFinishedRunDuringExport_changesNothing() throws Exception {
        final String id =
                fixture.importBook(TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.")), "en"), "en");
        final TranslationJob run =
                ok(fixture.engine().newJob(new RunRequest(id, ReviewMode.UNATTENDED), new PseudoChatModel(MAPPER)));
        ok(run.run());
        final RunRecord finished = ok(fixture.runs().latest(id)).orElseThrow();
        final BlockingClosePort port = new BlockingClosePort(fixture.documents(), 2);
        final ExportJob export =
                ok(fixture.serviceOver(port).newExport(request(id, tempDir.resolve("Book.uk.epub"), false), null));

        final Future<Result<ExportReport>> exporting = workers.submit(export::run);
        port.awaitBlockingClose();
        run.pause();
        port.releaseClose();

        assertThat(exporting.get(WAIT_SECONDS, TimeUnit.SECONDS).isOk()).isTrue();
        assertThat(run.state()).isEqualTo(JobState.COMPLETED);
        assertThat(ok(fixture.runs().latest(id))).contains(finished);
    }
}
