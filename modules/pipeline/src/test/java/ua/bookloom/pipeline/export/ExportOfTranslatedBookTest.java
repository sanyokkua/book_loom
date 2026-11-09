package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.MAPPER;
import static ua.bookloom.pipeline.export.ExportJobFixture.bodySegments;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.TestBooks;

/**
 * Pipeline e2e: a book translated with the pseudo model is written with the last check switched on and keeps its
 * segments. That the export reaches no provider is proved structurally by the ArchUnit rule
 * {@code export-makes-no-model-call} (no model type is reachable from the export package), not by this test.
 */
class ExportOfTranslatedBookTest {

    @TempDir
    private Path tempDir;

    @Test
    void run_afterAPseudoRun_writesTheBookWithEverySegment() {
        final ExportJobFixture fixture = new ExportJobFixture();
        final Path source = TestBooks.epubAtRoot(
                tempDir.resolve("Mini.epub"),
                List.of("ch01.xhtml", "ch02.xhtml"),
                List.of(
                        List.of("The lamp burned low.", "Rain fell on the roof."),
                        List.of("Morning came late.", "The road was empty.")));
        final String id = fixture.importBook(source, "en");
        ok(ok(fixture.engine().newJob(new RunRequest(id, ReviewMode.UNATTENDED), new PseudoChatModel(MAPPER)))
                .run());
        final Path destination = tempDir.resolve("Mini.uk.epub");

        ok(ok(fixture.service().newExport(new ExportRequest(id, destination, false, Set.of())))
                .run());

        assertThat(destination).isRegularFile();
        assertThat(bodySegments(ok(fixture.documents().open(destination))))
                .hasSameSizeAs(bodySegments(ok(fixture.documents().open(source))));
    }
}
