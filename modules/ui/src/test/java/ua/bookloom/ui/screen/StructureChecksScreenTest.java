package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The statistics card and the checks card beside the structure tree, read from the real scene: the eight rows, the
 * round trip running, passed and failed, the identifiers it names, the chunk-budget warning, and that no check ever
 * holds back Continue.
 */
class StructureChecksScreenTest extends StructureScreenTestBase {

    private static final RoundTripReport FAITHFUL = new RoundTripReport(true, true, List.of(), 1240, 1240);

    @TempDir
    private Path dir;

    private static BookPlan plan(final String... oversized) {
        return new BookPlan(Map.of("unit-0", 3), List.of(oversized));
    }

    private static ImportedBook frankenstein() {
        return BookFixtures.structured(
                "p1",
                BookFormat.EPUB,
                List.of(new StructureNode("Chapter 1", "unit-0", 1240, List.of())),
                new BookStats(1240, 78_214, 7, 0, 0, 0, 0, 0, Set.of(Formatting.ITALICS, Formatting.QUOTES)));
    }

    private boolean continueDisabled() {
        return ThemeTestSupport.onFx(() -> button("structure-continue").isDisabled());
    }

    private void showChecked(final RoundTripReport report, final BookPlan plan) throws TimeoutException {
        projects.onRoundTrip(Result.ok(report));
        projects.onPlan(Result.ok(plan));
        openBookThenShowStructure(dir.resolve("book.epub"), frankenstein());
        awaitChecksFinished();
    }

    // IF the card misread a figure, THEN the person would judge the book by numbers it does not have.
    @Test
    void statisticsCard_frankenstein_readsTheEightRowsInTheReferenceWords() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), frankenstein());

        assertThat(labelText("structure-stat-segments")).isEqualTo("1,240");
        assertThat(labelText("structure-stat-words")).isEqualTo("~78,000");
        assertThat(labelText("structure-stat-images")).isEqualTo("7");
        assertThat(labelText("structure-stat-code")).isEqualTo("0");
        assertThat(labelText("structure-stat-codeNote")).isEqualTo("preserved — never translated");
        assertThat(labelText("structure-stat-fonts")).isEqualTo("0");
        assertThat(labelText("structure-stat-verse")).isEqualTo("none");
        assertThat(labelText("structure-stat-notes")).isEqualTo("0 · 0");
        assertThat(labelText("structure-stat-formatting")).isEqualTo("italics, quotes → protected");
    }

    // IF the check hid the tree while it ran, THEN a slow book would leave the person staring at nothing.
    @Test
    void screen_roundTripNotAnswered_showsTreeStatisticsRunningCheckAndAvailableContinue() throws Exception {
        projects.holdRoundTrip();
        openBookThenShowStructure(dir.resolve("book.epub"), frankenstein());
        projects.awaitRoundTripEntered();

        assertThat(renderedRows()).containsExactly(List.of("Chapter 1", "1,240"));
        assertThat(labelText("structure-stat-segments")).isEqualTo("1,240");
        assertThat(labelText("structure-check-roundtrip")).startsWith("Checking");
        assertThat(continueDisabled()).isFalse();
        projects.releaseRoundTrip();
    }

    // IF a faithful book did not say so, THEN the person could not tell a passed check from one that never ran.
    @Test
    void checks_faithfulBook_showsBothPassedLines() throws TimeoutException {
        showChecked(FAITHFUL, plan());

        assertThat(labelText("structure-check-roundtrip"))
                .isEqualTo("Round-trip check passed — structure & text preserved");
        assertThat(labelText("structure-check-ids")).isEqualTo("All image / font / link IDs preserved");
    }

    // IF a lost identifier were not named, THEN the person could not find what the export would break.
    @Test
    void checks_missingImageId_namesItInPlaceOfThePreservedLine() throws TimeoutException {
        showChecked(new RoundTripReport(true, false, List.of("img-7"), 1240, 1240), plan());

        assertThat(labelText("structure-check-ids")).isEqualTo("IDs missing after the round trip: img-7");
    }

    // IF a dropped paragraph passed silently, THEN hours of translation would end in a book missing text.
    @Test
    void checks_oneSegmentDropped_failsAndNamesBothCounts() throws TimeoutException {
        showChecked(new RoundTripReport(false, true, List.of(), 1240, 1239), plan());

        assertThat(labelText("structure-check-roundtrip"))
                .isEqualTo("Round-trip check failed — structure not preserved (1,239 of 1,240 segments)");
    }

    // IF a failed check blocked Continue, THEN a person who wants a partial result could not go on.
    @Test
    void continueControl_failedCheck_isStillAvailableAndOpensNamesAndStyle() throws TimeoutException {
        showChecked(new RoundTripReport(false, false, List.of("img-7"), 1240, 1239), plan());

        assertThat(continueDisabled()).isFalse();
        onFx(() -> button("structure-continue").fire());

        assertThat(currentView()).isEqualTo(ViewNames.NAMES_STYLE);
    }

    // IF Continue did not mark the step, THEN the navigation would never show it done.
    @Test
    void continueControl_bookOpened_marksTheStructureStepDone() throws TimeoutException {
        showChecked(FAITHFUL, plan());

        onFx(() -> button("structure-continue").fire());

        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(WorkflowProgress.class).done().contains(ViewNames.STRUCTURE)))
                .isTrue();
    }

    // IF one oversized segment gave no warning, THEN the person would not know why a paragraph is translated in pieces.
    @Test
    void warning_oneOversizedSegment_saysItWillBeSplitBySentenceAndRejoined() throws TimeoutException {
        showChecked(FAITHFUL, plan("s-42"));

        assertThat(labelText("structure-chunk-warning-text"))
                .isEqualTo("1 segment exceeds the chunk budget — it will be split by sentence and re-joined.");
    }

    // IF a warning appeared with nothing oversized, THEN the person would worry about a book that is fine.
    @Test
    void warning_noOversizedSegment_isAbsent() throws TimeoutException {
        showChecked(FAITHFUL, plan());

        assertThat(optional("structure-chunk-warning")).isNull();
    }

    // IF a check that could not run showed as passed, THEN the person would trust a check nobody made.
    @Test
    void checks_roundTripUnavailable_saysSoAndKeepsContinueAvailable() throws TimeoutException {
        openBookThenShowStructure(dir.resolve("book.epub"), frankenstein());
        awaitFx(() -> !labelText("structure-check-roundtrip").startsWith("Checking"));

        assertThat(labelText("structure-check-roundtrip")).isEqualTo("The round-trip check could not run.");
        assertThat(continueDisabled()).isFalse();
    }

    // IF the check were asked for on the FX thread or for another project, THEN the window would freeze or check a
    // stale book.
    @Test
    void checks_bookOpened_askTheServiceOffTheFxThreadForTheOpenProject() throws TimeoutException {
        showChecked(FAITHFUL, plan());

        assertThat(projects.roundTripProjects()).containsExactly("p1");
        assertThat(projects.planProjects()).containsExactly("p1");
        assertThat(projects.roundTripCallsOnFxThread()).containsExactly(false);
        assertThat(projects.planCallsOnFxThread()).containsExactly(false);
    }
}
