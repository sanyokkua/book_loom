package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgress.Step;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestBooks;

/** An export tells its listener which step it is in and how many of the step's calls are finished. */
class ExportJobProgressTest {

    private static final String FIRST = "ch01.xhtml:0";
    private static final String SECOND = "ch01.xhtml:1";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private String projectId;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
        projectId = fixture.importBook(
                TestBooks.epubAtRoot(
                        tempDir.resolve("Book.epub"), List.of("ch01.xhtml"), List.of(List.of("One.", "Two."))),
                "en");
    }

    // The bar is determinate only if the total is announced before the first call: two flagged paragraphs, two calls.
    @Test
    void run_passWithTwoRiskyParagraphs_countsEachNeighbourCallOutOfTwo() {
        fixture.flag(projectId, FIRST, "Один.");
        fixture.flag(projectId, SECOND, "Два.");
        final ScriptedChatModel model = new ScriptedChatModel();
        model.answerTo("consistency", reply("Один."));
        model.answerTo("consistency", reply("Два."));
        final List<ExportProgress> seen = new CopyOnWriteArrayList<>();
        final ExportRequest request =
                new ExportRequest(projectId, tempDir.resolve("Book.uk.epub"), false, Set.of(), true);

        final ExportJob job = ok(fixture.service().newExport(request, model, seen::add));
        ok(job.run());

        assertThat(seen)
                .containsExactly(
                        new ExportProgress(Step.VALIDATING, 0, 1),
                        new ExportProgress(Step.CONSISTENCY_NEIGHBOUR, 0, 2),
                        new ExportProgress(Step.CONSISTENCY_NEIGHBOUR, 1, 2),
                        new ExportProgress(Step.CONSISTENCY_NEIGHBOUR, 2, 2),
                        new ExportProgress(Step.WRITING, 0, 1),
                        new ExportProgress(Step.WRITING, 1, 1));
    }

    // Without the pass there are no calls to count: only the checking and the writing are announced.
    @Test
    void run_passOff_announcesOnlyValidatingAndWriting() {
        final List<ExportProgress> seen = new CopyOnWriteArrayList<>();
        final ExportRequest request =
                new ExportRequest(projectId, tempDir.resolve("Book.uk.epub"), false, Set.of(), false);

        ok(ok(fixture.service().newExport(request, null, seen::add)).run());

        assertThat(seen).extracting(ExportProgress::step).containsExactly(Step.VALIDATING, Step.WRITING, Step.WRITING);
    }

    private static Result<ChatResponse> reply(final String target) {
        return Result.ok(new ChatResponse("{\"target\":\"" + target + "\"}", FinishReason.STOP));
    }
}
