package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ExportViewModel;

/** A segment written in its source for a broken translation is named on the Export screen, never hidden in a count. */
class ExportScreenFallbackTest extends TranslatingScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    @Test
    void screen_afterExportWithASourceFallback_namesItsLocator() throws TimeoutException {
        projects.on(EPUB, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(EPUB);
        chooseTarget();
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));
        awaitFx(() ->
                injector.getInstance(ExportViewModel.class).exportAvailable().get());
        ((ScriptedExportService) injector.getInstance(ExportService.class))
                .reportWith(new ExportReport(
                        Path.of("Frankenstein.uk.epub"),
                        9,
                        1,
                        0,
                        0,
                        9,
                        0,
                        List.of(),
                        10,
                        ConsistencySummary.NOT_RUN,
                        0,
                        List.of(new SourceFallback("part0009.html:1", "ch12 · p02"))));

        onFx(() -> button("export-run").fire());
        awaitFx(() -> injector.getInstance(ExportViewModel.class).outcome().get() != null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(textOf("export-check-source-fallbacks"))
                .contains("1 segment was written in the source language because its translation broke the"
                        + " formatting: ch12 · p02");
    }
}
