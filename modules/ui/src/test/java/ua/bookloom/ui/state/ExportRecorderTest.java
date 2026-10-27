package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.ui.RecordingProjectRepository;

/** A written and checked export is kept on its project so the tiles can come from it after a restart. */
class ExportRecorderTest {

    private static final Instant AT = Instant.parse("2026-10-22T12:00:00Z");

    private final RecordingProjectRepository projects = new RecordingProjectRepository();
    private final ExportRecorder recorder = new ExportRecorder(projects, () -> AT);

    private static ExportReport report() {
        return new ExportReport(
                Path.of("/out/Book.uk.epub"),
                70,
                72,
                3,
                4,
                50,
                6,
                List.of(Path.of("/out/Book.uk.glossary.csv")),
                70,
                ConsistencySummary.NOT_RUN,
                0);
    }

    // IF a field were dropped or swapped, THEN the tiles after a restart would show another export's figures.
    @Test
    void record_report_storesEachFigureAndTheTimeFromTheClock() {
        recorder.record("p1", report());

        assertThat(projects.recorded())
                .containsExactly(new ExportRecord(
                        AT,
                        Path.of("/out/Book.uk.epub"),
                        70,
                        72,
                        3,
                        4,
                        50,
                        6,
                        70,
                        List.of(Path.of("/out/Book.uk.glossary.csv"))));
    }

    @Test
    void record_report_isReadBackAsTheProjectsLastExport() {
        recorder.record("p1", report());

        assertThat(projects.lastExport("p1").data())
                .hasValueSatisfying(last -> assertThat(last.written()).isEqualTo(70));
    }

    // IF a failed save broke the export, THEN a finished book would read as lost over a bookkeeping error.
    @Test
    void record_storeFails_doesNotThrowAndStoresNothing() {
        projects.failRecordingWith(AppError.of(ErrorCode.internal, "Not saved", "The store is closed."));

        assertThatCode(() -> recorder.record("p1", report())).doesNotThrowAnyException();
        assertThat(projects.recorded()).isEmpty();
    }
}
