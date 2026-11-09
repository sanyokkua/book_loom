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
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgress.Step;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.pipeline.TestBooks;

/** An export tells its listener which step it is in. */
class ExportJobProgressTest {

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

    // The export calls no model, so only the checking and the writing are announced.
    @Test
    void run_passOff_announcesOnlyValidatingAndWriting() {
        final List<ExportProgress> seen = new CopyOnWriteArrayList<>();
        final ExportRequest request =
                new ExportRequest(projectId, tempDir.resolve("Book.uk.epub"), false, Set.of(), false);

        ok(ok(fixture.service().newExport(request, null, seen::add)).run());

        assertThat(seen).extracting(ExportProgress::step).containsExactly(Step.VALIDATING, Step.WRITING, Step.WRITING);
    }
}
