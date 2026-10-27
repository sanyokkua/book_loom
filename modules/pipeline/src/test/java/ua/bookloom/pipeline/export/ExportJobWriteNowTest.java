package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.hiddenFiles;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.zipEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestBooks;

/**
 * "Write the file now" ends the last check early: the call in flight is given up, no further model call is sent,
 * every fix already stored stays, the items not reached stay as they are and are reported as not reread, and the book
 * is written. It does nothing when no check runs or the file is already being written.
 */
class ExportJobWriteNowTest {

    private static final String FIRST = "ch01.xhtml:0";
    private static final String FIXED = "Вона втомилася після довгої дороги додому.";
    private static final String STORED = "Вона втомився після довгої дороги додому.";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private String projectId;
    private Path destination;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
        projectId = fixture.importBook(
                TestBooks.epubAtRoot(
                        tempDir.resolve("Book.epub"),
                        List.of("ch01.xhtml"),
                        List.of(List.of("She was tired after the long road home.", "Two.", "Three."))),
                "en");
        destination = tempDir.resolve("Book.uk.epub");
    }

    private void flagAllThree() {
        fixture.flag(projectId, FIRST, STORED);
        fixture.flag(projectId, "ch01.xhtml:1", "Два.");
        fixture.flag(projectId, "ch01.xhtml:2", "Три.");
    }

    private ExportRequest withPass() {
        return new ExportRequest(projectId, destination, false, Set.of(), true);
    }

    private ExportRequest withoutPass() {
        return new ExportRequest(projectId, destination, false, Set.of(), false);
    }

    private static Result<ChatResponse> reply(final String target) {
        return Result.ok(new ChatResponse("{\"target\":\"" + target + "\"}", FinishReason.STOP));
    }

    // IF Write now waited for the call in flight, THEN a slow model would keep the person from their file.
    @Test
    void writeNow_duringSecondNeighbourCall_writesFixesSoFarAndCountsTheRestNotReread() {
        flagAllThree();
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo("consistency", reply(FIXED)).blockNthRequest(2);
        final ExportJob job = ok(fixture.service().newExport(withPass(), model));
        final CompletableFuture<Result<ExportReport>> running = CompletableFuture.supplyAsync(job::run);
        model.awaitRequests(2);

        job.writeNow();

        final ExportReport report = ok(running.orTimeout(10, TimeUnit.SECONDS).join());
        assertThat(zipEntry(destination, "ch01.xhtml")).contains(FIXED, "Два.", "Три.");
        assertThat(model.requests()).hasSize(2);
        assertThat(report.consistency().checks().notReread()).isEqualTo(2);
        assertThat(report.consistency().neighbourFixes()).isEqualTo(1);
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // IF the stored fix were rolled back with the early stop, THEN Write now would throw away the work it keeps.
    @Test
    void writeNow_afterAFix_keepsTheStoredFixInTheProject() {
        flagAllThree();
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo("consistency", reply(FIXED)).blockNthRequest(2);
        final ExportJob job = ok(fixture.service().newExport(withPass(), model));
        final CompletableFuture<Result<ExportReport>> running = CompletableFuture.supplyAsync(job::run);
        model.awaitRequests(2);

        job.writeNow();
        running.orTimeout(10, TimeUnit.SECONDS).join();

        assertThat(fixture.records(projectId))
                .filteredOn(record -> record.segmentId().equals(FIRST))
                .extracting(record -> record.machineTarget())
                .containsExactly(FIXED);
    }

    // IF a Write now with no pass to end made an error or skipped the write, THEN a stray click would lose the export.
    @Test
    void writeNow_withoutPass_isIgnoredAndWritesTheBook() {
        flagAllThree();
        final ExportJob job = ok(fixture.service().newExport(withoutPass(), null));

        job.writeNow();
        final ExportReport report = ok(job.run());

        assertThat(zipEntry(destination, "ch01.xhtml")).contains(STORED, "Два.", "Три.");
        assertThat(report.consistency().checks().notReread()).isZero();
    }

    // IF Write now after the file began to be written changed anything, THEN a late click could corrupt the checked
    // file.
    @Test
    void writeNow_afterWritingStarted_changesNothing() {
        flagAllThree();
        final AtomicReference<ExportJob> running = new AtomicReference<>();
        final List<ExportProgress.Step> steps = new CopyOnWriteArrayList<>();
        final ExportProgressListener listener = reached -> {
            steps.add(reached.step());
            if (reached.step() == ExportProgress.Step.WRITING) {
                java.util.Objects.requireNonNull(running.get(), "job").writeNow();
            }
        };
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(3, reply(FIXED));
        final ExportJob job = ok(fixture.service().newExport(withPass(), model, listener));
        running.set(job);

        final ExportReport report = ok(job.run());

        assertThat(steps).contains(ExportProgress.Step.WRITING);
        assertThat(report.consistency().checks().notReread()).isZero();
        assertThat(destination).isRegularFile();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // Recording the export belongs to the window: the engine's job leaves the project's stored record alone.
    @Test
    void run_completedExport_recordsNothingInTheProjectStore() {
        ok(fixture.export(ExportJobFixture.request(projectId, destination, false)));

        assertThat(ok(fixture.projects().lastExport(projectId))).isEmpty();
    }
}
