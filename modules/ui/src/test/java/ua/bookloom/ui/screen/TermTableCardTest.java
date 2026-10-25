package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.PriorityRow;
import ua.bookloom.ui.control.TwoColumns;

/**
 * Names &amp; style as two equal cards built from one {@link TermTableCard} (15i.U5): the Glossary and the Recurring
 * terms card carry the same toolbar in the same order, look the same, fold Import and Export into a menu first when
 * the card is narrow, stack below the breakpoint of the screen's content width and sit side by side above it.
 */
class TermTableCardTest extends TermTableCardTestBase {

    // ---- one card, built twice -------------------------------------------------------------------------------

    // IF the two cards were built from different code, THEN their toolbars and tables would drift apart again.
    @ParameterizedTest
    @CsvSource({"names-style-card", "recurring-card"})
    void card_eitherSide_isATermTableCard(final String id) throws Exception {
        showScreen("en", 1024, 700, ROSALIND);

        assertThat(required(id)).isInstanceOf(TermTableCard.class);
    }

    // IF a card lacked a button or kept it elsewhere, THEN the person would learn the Glossary's toolbar and meet
    // another one on the Recurring terms card.
    @ParameterizedTest
    @CsvSource({"en, names-style", "en, recurring", "uk, names-style", "uk, recurring"})
    void toolbar_roomToSpare_offersSearchAddThenTheThreeModelActionsThenImportAndExport(
            final String tag, final String card) throws Exception {
        showScreen(tag, 1300, 800, ROSALIND);

        assertThat(shownParts(card)).containsExactlyElementsOf(UNFOLDED);
    }

    // IF the toolbar were a wrapping pane, THEN a narrow card would grow a second row instead of folding.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void toolbar_eitherCard_isAPriorityRow(final String card) throws Exception {
        showScreen("en", 1024, 700, ROSALIND);

        assertThat(required(card + "-toolbar")).isInstanceOf(PriorityRow.class);
    }

    // IF the cards looked different, THEN the same list would read as two unrelated widgets.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void table_eitherCard_hasTheSameLookAndRowHeight(final String card) throws Exception {
        showScreen("en", 1024, 700, ROSALIND);

        assertThat(table(card).getStyleClass()).contains("glossary-table");
        assertThat(table(card).getFixedCellSize()).isEqualTo(46.0);
        assertThat(required(card + "-card").getStyleClass()).contains("card");
    }

    // ---- the narrow card folds Import and Export first ----------------------------------------------------------

    // IF the narrow card dropped Scan or Add before Import and Export, THEN the actions a person uses most would be
    // the ones to vanish; Import and Export fold into the three-dot menu first.
    @ParameterizedTest
    @CsvSource({
        "en, 960, 640, names-style",
        "en, 960, 640, recurring",
        "en, 980, 700, names-style",
        "en, 980, 700, recurring",
        "uk, 960, 640, names-style",
        "uk, 960, 640, recurring",
        "uk, 980, 700, names-style",
        "uk, 980, 700, recurring"
    })
    void toolbar_narrowCard_foldsImportAndExportIntoTheMenuAndKeepsTheRest(
            final String tag, final double width, final double height, final String card) throws Exception {
        showScreen(tag, width, height, ROSALIND);

        assertThat(shownParts(card)).containsExactlyElementsOf(FOLDED);
    }

    @ParameterizedTest
    @CsvSource({"en, names-style, Import..., Export...", "uk, recurring, Імпорт..., Експорт..."})
    void menu_foldedToolbar_holdsImportThenExportWithTheirOwnWords(
            final String tag, final String card, final String importCaption, final String exportCaption)
            throws Exception {
        showScreen(tag, 960, 640, ROSALIND);

        final MenuButton more = (MenuButton) required(card + "-more");
        final List<MenuItem> files = ThemeTestSupport.onFx(() -> more.getItems().stream()
                .filter(item -> item.getId().endsWith("-import") || item.getId().endsWith("-export"))
                .toList());

        assertThat(files).extracting(MenuItem::getText).containsExactly(importCaption, exportCaption);
        assertThat(files).extracting(MenuItem::getId).containsExactly(card + "-more-import", card + "-more-export");
    }

    // IF the menu showed Review and Translate while their buttons are in the row, THEN each action would be offered
    // twice; an item shows only once its button has folded into the menu.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void menu_reviewAndTranslateButtonsShown_itsItemsForThemAreHidden(final String card) throws Exception {
        showScreen("en", 1300, 800, ROSALIND);

        final MenuButton more = (MenuButton) required(card + "-more");

        assertThat(ThemeTestSupport.onFx(() -> more.getItems().stream()
                        .filter(item ->
                                item.getId().endsWith("-review") || item.getId().endsWith("-translate"))
                        .map(MenuItem::isVisible)
                        .toList()))
                .containsExactly(false, false);
    }

    /** The scene bounds of every toolbar part of a card that a person can see. */
    private List<javafx.geometry.Bounds> partBounds(final String card) {
        return ThemeTestSupport.onFx(() -> descendants(required(card + "-toolbar")).stream()
                .filter(node -> node.getId() != null && node.getId().startsWith(card + "-"))
                .filter(node -> TOOLBAR_PARTS.contains(node.getId().substring(card.length() + 1)))
                .filter(node -> node.isVisible() && node.isManaged())
                .map(node -> node.localToScene(node.getBoundsInLocal()))
                .toList());
    }

    private void assertOneLineInsideItsCard(final String card) {
        final double cardRight = ThemeTestSupport.onFx(() -> required(card + "-card")
                .localToScene(required(card + "-card").getBoundsInLocal())
                .getMaxX());
        final List<javafx.geometry.Bounds> parts = partBounds(card);
        final double firstTop = parts.get(0).getMinY();
        assertThat(parts).isNotEmpty().allSatisfy(bounds -> {
            assertThat(bounds.getMinY()).isCloseTo(firstTop, within(2.0));
            assertThat(bounds.getMaxX()).isLessThanOrEqualTo(cardRight);
        });
    }

    // IF the toolbar broke onto a second line or ran past the card, THEN the squeeze would look like a defect.
    @ParameterizedTest
    @CsvSource({"en, 960, 640", "en, 1024, 700", "en, 1920, 700", "uk, 960, 640", "uk, 1024, 700", "uk, 1920, 700"})
    void toolbar_atEachWindowSize_staysOnOneLineInsideItsCard(final String tag, final double width, final double height)
            throws Exception {
        showScreen(tag, width, height, ROSALIND);

        assertOneLineInsideItsCard("names-style");
        assertOneLineInsideItsCard("recurring");
    }

    // IF Stop pushed a caption off the row while the model works, THEN the one way out of a slow request would be
    // unreadable; Import and Export stay folded and the row stays whole.
    @ParameterizedTest
    @CsvSource({"en, 960, 640", "en, 1024, 700", "uk, 960, 640", "uk, 1024, 700"})
    void toolbar_modelActionRunning_showsStopOnBothCardsAndStaysOnOneLine(
            final String tag, final double width, final double height) throws Exception {
        showScreen(tag, width, height, ROSALIND);
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(0, 0, List.of())));
        glossary.holdModelCalls();

        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> isShown("names-style-stop") && isShown("recurring-stop"));

        assertThat(shownParts("names-style"))
                .containsExactly("search", "add", "scan", "review", "translate", "stop", "more");
        assertThat(shownParts("recurring"))
                .containsExactly("search", "add", "scan", "review", "translate", "stop", "more");
        assertThat(minY("recurring-stop")).isCloseTo(minY("recurring-scan"), within(2.0));
        assertThat(minY("names-style-stop")).isCloseTo(minY("names-style-scan"), within(2.0));
    }

    // ---- two equal cards: stacked below the breakpoint, side by side above ------------------------------------

    // IF the screen kept the cards stacked on a wide window, THEN half the width would sit empty; IF it split a narrow
    // one, THEN both tables would be unreadably narrow. The switch is the content area's WIDE class (1440 px).
    @ParameterizedTest
    @CsvSource({"1280, 800, true", "1660, 900, true", "1700, 900, false", "1920, 700, false"})
    void cards_atEachWindowSize_stackBelowTheBreakpointAndSitSideBySideAbove(
            final double width, final double height, final boolean stacked) throws Exception {
        showScreen("en", width, height, ROSALIND);

        final TwoColumns columns = (TwoColumns) required("names-style-card").getParent();
        assertThat(required("recurring-card").getParent()).isSameAs(columns);
        assertThat(columns.sideBySideFrom()).isEqualTo(LayoutClass.Width.WIDE);
        assertThat(ThemeTestSupport.onFx(columns::isStacked)).isEqualTo(stacked);
    }

    @Test
    void cards_stacked_putGlossaryAboveRecurringTermsAtTheSameWidth() throws Exception {
        showScreen("en", 1024, 700, ROSALIND);

        final var glossaryBounds = ThemeTestSupport.onFx(() -> required("names-style-card")
                .localToScene(required("names-style-card").getBoundsInLocal()));
        final var recurringBounds = ThemeTestSupport.onFx(() -> required("recurring-card")
                .localToScene(required("recurring-card").getBoundsInLocal()));

        assertThat(recurringBounds.getMinY()).isGreaterThanOrEqualTo(glossaryBounds.getMaxY());
        assertThat(recurringBounds.getMinX()).isEqualTo(glossaryBounds.getMinX());
        assertThat(recurringBounds.getWidth()).isEqualTo(glossaryBounds.getWidth());
    }

    @Test
    void cards_sideBySide_areEqualInWidthWithTheGlossaryOnTheLeft() throws Exception {
        showScreen("en", 1920, 700, ROSALIND);

        final var glossaryBounds = ThemeTestSupport.onFx(() -> required("names-style-card")
                .localToScene(required("names-style-card").getBoundsInLocal()));
        final var recurringBounds = ThemeTestSupport.onFx(() -> required("recurring-card")
                .localToScene(required("recurring-card").getBoundsInLocal()));

        assertThat(recurringBounds.getMinY()).isEqualTo(glossaryBounds.getMinY());
        assertThat(recurringBounds.getMinX()).isGreaterThanOrEqualTo(glossaryBounds.getMaxX());
        assertThat(recurringBounds.getWidth()).isCloseTo(glossaryBounds.getWidth(), within(1.0));
        assertThat(recurringBounds.getHeight()).isCloseTo(glossaryBounds.getHeight(), within(1.0));
    }
}
