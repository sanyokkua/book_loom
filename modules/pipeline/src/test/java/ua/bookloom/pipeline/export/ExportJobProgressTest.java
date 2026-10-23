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

        assertThat(seen).containsExactly(twoUnchangedParagraphs());
    }

    // Both paragraphs answered unchanged, each announced with the paragraph beside it.
    private static ExportProgress[] twoUnchangedParagraphs() {
        return new ExportProgress[] {
            new ExportProgress(Step.VALIDATING, 0, 1),
            new ExportProgress(Step.CONSISTENCY_NEIGHBOUR, 0, 2),
            new ExportProgress(
                    Step.CONSISTENCY_NEIGHBOUR,
                    1,
                    2,
                    FIRST,
                    "Один.",
                    null,
                    "unchanged",
                    new ExportProgress.Neighbours(null, null, "One.", "Two.", "Два.")),
            new ExportProgress(
                    Step.CONSISTENCY_NEIGHBOUR,
                    2,
                    2,
                    SECOND,
                    "Два.",
                    null,
                    "unchanged",
                    new ExportProgress.Neighbours("One.", "Один.", "Two.", null, null)),
            new ExportProgress(Step.WRITING, 0, 1),
            new ExportProgress(Step.WRITING, 1, 1)
        };
    }

    // IF the dialog got no before and after, THEN a fixed paragraph could not be shown next to what it was.
    @Test
    void export_neighbourFix_announcesBeforeAfterFixed() {
        // Three words, one of them replaced, is within the rewrite cap; a one-word paragraph never could be.
        final String fixProject = fixture.importBook(
                TestBooks.epubAtRoot(
                        tempDir.resolve("Fix.epub"), List.of("ch01.xhtml"), List.of(List.of("He went home."))),
                "en");
        fixture.flag(fixProject, FIRST, "Він йшов додому.");
        final ScriptedChatModel model = new ScriptedChatModel();
        model.answerTo("consistency", reply("Він ішов додому."));
        final List<ExportProgress> seen = new CopyOnWriteArrayList<>();
        final ExportRequest request =
                new ExportRequest(fixProject, tempDir.resolve("Book.uk.epub"), false, Set.of(), true);

        ok(ok(fixture.service().newExport(request, model, seen::add)).run());

        assertThat(seen)
                .contains(new ExportProgress(
                        Step.CONSISTENCY_NEIGHBOUR,
                        1,
                        1,
                        FIRST,
                        "Він йшов додому.",
                        "Він ішов додому.",
                        "fixed",
                        new ExportProgress.Neighbours(null, null, "He went home.", null, null)));
    }

    // A paragraph the model leaves alone has an item with no after, so the dialog shows only the before.
    @Test
    void export_neighbourUnchanged_announcesNullAfterUnchanged() {
        fixture.flag(projectId, FIRST, "Один.");
        final ScriptedChatModel model = new ScriptedChatModel();
        model.answerTo("consistency", reply("Один."));
        final List<ExportProgress> seen = new CopyOnWriteArrayList<>();
        final ExportRequest request =
                new ExportRequest(projectId, tempDir.resolve("Book.uk.epub"), false, Set.of(), true);

        ok(ok(fixture.service().newExport(request, model, seen::add)).run());

        assertThat(seen)
                .contains(new ExportProgress(
                        Step.CONSISTENCY_NEIGHBOUR,
                        1,
                        1,
                        FIRST,
                        "Один.",
                        null,
                        "unchanged",
                        new ExportProgress.Neighbours(null, null, "One.", null, null)));
    }

    // IF a remembered paragraph counted no item, THEN the bar of a second export could never reach its total.
    @Test
    void export_rememberedParagraph_countsItemWithoutCall() {
        fixture.flag(projectId, FIRST, "Один.");
        final ScriptedChatModel model = new ScriptedChatModel();
        model.answerTo("consistency", reply("Один."));
        final ExportRequest first = new ExportRequest(projectId, tempDir.resolve("A.uk.epub"), false, Set.of(), true);
        ok(ok(fixture.service().newExport(first, model, progress -> {})).run());
        final List<ExportProgress> seen = new CopyOnWriteArrayList<>();
        final ExportRequest again = new ExportRequest(projectId, tempDir.resolve("B.uk.epub"), false, Set.of(), true);

        ok(ok(fixture.service().newExport(again, model, seen::add)).run());

        assertThat(model.requests()).hasSize(1);
        assertThat(seen)
                .contains(new ExportProgress(
                        Step.CONSISTENCY_NEIGHBOUR,
                        1,
                        1,
                        FIRST,
                        "Один.",
                        null,
                        "remembered",
                        new ExportProgress.Neighbours(null, null, "One.", null, null)));
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
