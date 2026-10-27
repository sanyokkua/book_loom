package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.project.ExportRecord;

/** A finished export is recorded on its project by the window, once it was written and checked, and only then. */
class ExportScreenRecordTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void exportAndAwait() throws TimeoutException {
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
        onFx(() -> button("export-run").fire());
    }

    // IF nothing recorded the export, THEN the tiles would forget it at the next start.
    @Test
    void export_writtenAndChecked_recordsItsFiguresOnTheProject() throws TimeoutException {
        exportService()
                .reportWith(new ExportReport(
                        Path.of("Frankenstein.uk.epub"),
                        70,
                        72,
                        0,
                        0,
                        50,
                        6,
                        List.of(),
                        70,
                        ConsistencySummary.NOT_RUN,
                        0));

        exportAndAwait();
        awaitFx(() -> !repository().recorded().isEmpty());

        assertThat(repository().recorded()).hasSize(1);
        final ExportRecord record = repository().recorded().getFirst();
        assertThat(record.written()).isEqualTo(70);
        assertThat(record.pending()).isEqualTo(72);
        assertThat(record.destination()).isEqualTo(Path.of("Frankenstein.uk.epub"));
        assertThat(record.exportedAt()).isEqualTo(ua.bookloom.ui.UiTestInjector.NOW);
    }

    // IF a refused export were recorded, THEN the tiles would claim a file that was never written.
    @Test
    void export_refused_recordsNothing() throws TimeoutException {
        exportService().failWith(AppError.of(ErrorCode.validation, "Refused", "Frankenstein.uk.epub is taken."));

        exportAndAwait();
        awaitFx(() -> !exports().failure().get().isEmpty());

        assertThat(repository().recorded()).isEmpty();
    }

    // IF a failed save of the record hid the result, THEN a finished book would read as lost over bookkeeping.
    @Test
    void export_recordCannotBeSaved_stillShowsTheExportComplete() throws TimeoutException {
        repository().failRecordingWith(AppError.of(ErrorCode.internal, "Not saved", "The store is closed."));
        exportService().reportWith(report(Path.of("Frankenstein.uk.epub"), List.of(), ConsistencySummary.NOT_RUN));

        exportAndAwait();
        awaitFx(() -> exports().outcome().get() != null);

        assertThat(optional("export-complete-card")).isNotNull();
        assertThat(repository().recorded()).isEmpty();
    }
}
