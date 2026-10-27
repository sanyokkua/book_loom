package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The import screen's technical lines (round trip, identifiers, the group warning, tokens and group badges) appear
 * only with Show technical details on; by default a passed check is one plain line.
 */
class ImportTechnicalLinesTest extends StructureScreenTestBase {

    private static final RoundTripReport FAITHFUL = new RoundTripReport(true, true, List.of(), 4, 4);

    @TempDir
    private Path dir;

    private void openChecked(final RoundTripReport report) throws TimeoutException {
        projects.onRoundTrip(Result.ok(report));
        projects.onPlan(Result.ok(new BookPlan(Map.of("unit-0", 3), List.of("s-2"))));
        projects.onSegments(
                "unit-0",
                Result.ok(List.of(new SegmentPreview(
                        "s0", "ch1 · p01", SegmentKind.PARAGRAPH, "Words", "Words", 5, false, false, 1, 2, false))));
        openBookThenShowStructure(
                dir.resolve("book.epub"), BookFixtures.imported("p", BookFormat.EPUB, null, null, null, 4, 1));
        awaitChecksFinished();
        awaitFx(() -> !((ListView<?>) required("structure-segments")).getItems().isEmpty());
    }

    private boolean visible(final String selector) {
        return ThemeTestSupport.onFx(() -> {
            final Node node = required("structure-segments").lookup(selector);
            return node != null && node.isVisible();
        });
    }

    // IF the facts card always named the round trip and the identifiers, THEN a person would meet engine words.
    @Test
    void checks_technicalOff_aPassedBookReadsOnePlainLine() throws TimeoutException {
        openChecked(FAITHFUL);

        assertThat(labelText("structure-check-roundtrip")).isEqualTo("Read and written back with nothing lost");
        assertThat(optional("structure-check-ids")).isNull();
        assertThat(optional("structure-chunk-warning")).isNull();
    }

    // IF a failed check were hidden with the technical lines, THEN a damaged round trip would read as fine.
    @Test
    void checks_technicalOff_aMissingIdIsStillNamed() throws TimeoutException {
        openChecked(new RoundTripReport(true, false, List.of("img-1"), 4, 4));

        assertThat(labelText("structure-check-ids")).isEqualTo("IDs missing after the round trip: img-1");
    }

    // IF the switch did not reach a screen already open, THEN the person would have to reopen the book.
    @Test
    void checks_technicalSwitchedOn_showBothLinesAndTheGroupWarning() throws TimeoutException {
        openChecked(FAITHFUL);

        technicalDetails(true);

        assertThat(labelText("structure-check-roundtrip"))
                .isEqualTo("Round-trip check passed — structure & text preserved");
        assertThat(labelText("structure-check-ids")).isEqualTo("All image / font / link IDs preserved");
        assertThat(optional("structure-chunk-warning")).isNotNull();
    }

    // IF each row carried its token count and group badge, THEN the list would show figures only a developer reads.
    @Test
    void rows_technicalOff_hideTokensAndGroupBadge() throws TimeoutException {
        openChecked(FAITHFUL);

        assertThat(visible(".structure-seg-tokens")).isFalse();
        assertThat(visible(".structure-seg-chunk")).isFalse();
    }

    // IF the rows ignored the switch, THEN turning it on would show no tokens or groups.
    @Test
    void rows_technicalSwitchedOn_showTokensAndGroupBadge() throws TimeoutException {
        openChecked(FAITHFUL);

        technicalDetails(true);

        assertThat(visible(".structure-seg-tokens")).isTrue();
        assertThat(visible(".structure-seg-chunk")).isTrue();
    }

    // IF the reading pane's line named tokens and groups by default, THEN it would say more than where the text is.
    @Test
    void preview_technicalOff_saysOnlyThePlaceAndTheKind() throws TimeoutException {
        openChecked(FAITHFUL);

        onFx(() -> ((ListView<?>) required("structure-segments"))
                .getSelectionModel()
                .select(0));

        assertThat(labelText("structure-preview-meta")).isEqualTo("ch1 · p01 · Paragraph");
    }
}
