package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.state.ExportOutcome;

/** The export-complete card says what the last check did, and what an early write left unread. */
class ExportCompleteDialogLastCheckTest extends ShellTestBase {

    private static final Path BOOK = Path.of("/books/Frankenstein.uk.epub");
    private static final String CARD = ExportCompleteDialog.CARD_ID;

    private void show(final ConsistencySummary consistency) {
        final ExportReport report = new ExportReport(BOOK, 10, 0, 0, 0, 8, 2, List.of(), 10, consistency, 0);
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, 1)));
    }

    private static ConsistencySummary ran(final int fixes, final int unchanged, final int notReread) {
        return new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                Map.of(),
                fixes,
                new ConsistencyChecks(0, 0, unchanged, Map.of(), 0, notReread));
    }

    // IF the dialog said nothing of the last check, THEN a person could not tell it had run, or how much it touched.
    @Test
    void show_lastCheckRan_hasALastCheckRowWithTheRereadAndFixedCounts() {
        show(ran(1, 17, 0));

        assertThat(textsUnder(required(CARD))).contains("Last check", "18 segments reread · 1 fixed");
    }

    @Test
    void show_lastCheckRereadOneSegment_usesTheSingularForm() {
        show(ran(0, 1, 0));

        assertThat(textsUnder(required(CARD))).contains("1 segment reread · 0 fixed");
    }

    // IF the row showed with no check, THEN a person who switched it off would read "0 segments reread".
    @Test
    void show_lastCheckNotRun_hasNoLastCheckRow() {
        show(ConsistencySummary.NOT_RUN);

        assertThat(textsUnder(required(CARD))).doesNotContain("Last check");
    }

    // IF an early write reported its whole check as done, THEN the person would trust paragraphs nobody reread.
    @Test
    void show_writtenEarly_saysHowManyParagraphsWereNotReread() {
        show(ran(1, 5, 12));

        assertThat(String.join(" ", textsUnder(required(CARD))))
                .contains("12 paragraphs were not reread: the file was written before the last check reached them");
    }

    @Test
    void show_oneParagraphNotReread_usesTheSingularForm() {
        show(ran(0, 5, 1));

        assertThat(String.join(" ", textsUnder(required(CARD))))
                .contains("1 paragraph was not reread: the file was written before the last check reached it");
    }
}
