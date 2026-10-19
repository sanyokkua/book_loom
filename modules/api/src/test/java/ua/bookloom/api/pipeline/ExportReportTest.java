package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ExportReport}'s defensive copy of its side-file list and its negative-count guard.
 */
class ExportReportTest {

    @Test
    void constructor_sideFilesList_isDefensivelyCopied() {
        final Path sideFile = Path.of("Book.glossary.csv");
        final List<Path> sideFiles = new ArrayList<>(List.of(sideFile));

        final ExportReport report = new ExportReport(
                Path.of("Book.uk.md"), 10, 1, 0, 1, 8, 9, sideFiles, 10, ConsistencySummary.NOT_RUN, 0);
        sideFiles.clear();

        assertThat(report.sideFiles()).containsExactly(sideFile);
    }

    @Test
    void constructor_negativeCount_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExportReport(
                        Path.of("Book.uk.md"), -1, 0, 0, 0, 0, 0, List.of(), 0, ConsistencySummary.NOT_RUN, 0));
    }
}
