package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ExportRecord;

/**
 * The four tiles are never blank: before any export they come from the project's counts, and after an export from the
 * stored record of it, also after a restart. Nothing else on the screen hints at a file that does not exist yet.
 */
class ExportScreenTilesTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    // IF the tiles stayed blank until an export, THEN the screen would look empty and say nothing of the book.
    @Test
    void tiles_beforeAnyExport_read74Of142FromTheProject() throws TimeoutException {
        desk.willAnswerCounts(counts(68));
        openAndShowExport(EPUB);

        awaitFx(() -> "74 of 142".equals(labelText("export-written-value")));

        assertThat(textOf("export-written")).contains("segments done");
        assertThat(labelText("export-auto-value")).isEqualTo("50");
        assertThat(labelText("export-reviewed-value")).isEqualTo("6");
        assertThat(labelText("export-valid-value")).isEqualTo("—");
        assertThat(textOf("export-valid")).contains("checked when written");
    }

    // IF checks and result actions showed before an export, THEN they would vouch for a file that does not exist.
    @Test
    void tiles_beforeAnyExport_showNoChecksAndNoOpenActions() throws TimeoutException {
        desk.willAnswerCounts(counts(68));
        openAndShowExport(EPUB);
        awaitFx(() -> "74 of 142".equals(labelText("export-written-value")));

        assertThat(optional("export-check-reopened")).isNull();
        assertThat(isShown("export-checks")).isFalse();
        assertThat(isShown("export-open-book")).isFalse();
    }

    // IF the stored record were ignored, THEN a restart would make the screen forget what was written.
    @Test
    void tiles_withAStoredExportRecord_comeFromItEvenWhenTheLiveCountsAreOtherwise() throws TimeoutException {
        repository()
                .recordExport(
                        "p1",
                        new ExportRecord(
                                Instant.parse("2026-10-20T09:00:00Z"),
                                Path.of("Frankenstein.uk.epub"),
                                70,
                                72,
                                0,
                                0,
                                50,
                                6,
                                70,
                                List.of()));
        desk.willAnswerCounts(counts(68));
        openAndShowExport(EPUB);

        awaitFx(() -> "70 of 142".equals(labelText("export-written-value")));

        assertThat(textOf("export-written")).contains("segments written");
        assertThat(labelText("export-valid-value")).isEqualTo("✓");
        assertThat(textOf("export-valid")).contains("checked: opens, nothing missing");
    }

    // The auto and reviewed tiles are the project's live figures, not part of what the record froze.
    @Test
    void tiles_withAStoredExportRecord_keepTheAutoAndReviewedTilesLive() throws TimeoutException {
        repository()
                .recordExport(
                        "p1",
                        new ExportRecord(
                                Instant.parse("2026-10-20T09:00:00Z"),
                                Path.of("Frankenstein.uk.epub"),
                                70,
                                72,
                                0,
                                0,
                                1,
                                1,
                                70,
                                List.of()));
        desk.willAnswerCounts(counts(68));
        openAndShowExport(EPUB);

        awaitFx(() -> "70 of 142".equals(labelText("export-written-value")));

        assertThat(labelText("export-auto-value")).isEqualTo("50");
        assertThat(labelText("export-reviewed-value")).isEqualTo("6");
    }
}
