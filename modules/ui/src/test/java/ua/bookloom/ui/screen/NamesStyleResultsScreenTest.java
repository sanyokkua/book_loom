package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * What a person sees of an operation's results on Names &amp; style: the card that opens when it ends, the marker on
 * the rows it changed and the filter that keeps only them until the next operation.
 */
class NamesStyleResultsScreenTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry WELL =
            new GlossaryEntry("e1", "p1", "Well", null, TermType.OTHER, Gender.UNKNOWN, false);
    private static final GlossaryEntry HALE =
            new GlossaryEntry("e2", "p1", "Hale", null, TermType.OTHER, Gender.UNKNOWN, false);
    private static final GlossaryEntry WELL_TYPED =
            new GlossaryEntry("e1", "p1", "Well", null, TermType.CHARACTER, Gender.FEMALE, false);

    private void showScreen() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !glossaryTable().getItems().isEmpty());
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
    }

    @SuppressWarnings("unchecked")
    private TableView<GlossaryEntry> glossaryTable() {
        return (TableView<GlossaryEntry>) required("names-style-table");
    }

    private boolean isCardShown() {
        final Node card = scene.getRoot().lookup("#results-card");
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private long visibleMarkers(final String tableId) {
        return ThemeTestSupport.onFx(() -> required(tableId).lookupAll(".changed-marker").stream()
                .filter(node -> node.isVisible() && node.getParent() != null)
                .count());
    }

    private void reviewTypingWell() throws Exception {
        final EntryChanges<GlossaryEntry> changes =
                new EntryChanges<>(List.of(), List.of(), List.of(new EntryChanges.Change<>(WELL, WELL_TYPED, null)));
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(0, 1, 0, List.of(WELL_TYPED, HALE), changes)));
        onFx(() -> button("names-style-review").fire());
        awaitFx(this::isCardShown);
    }

    // IF the results card did not open on its own, THEN the person would have to guess what the model did.
    @Test
    void review_ends_opensTheResultsCardWithTheChangedRow() throws Exception {
        showScreen();

        reviewTypingWell();

        assertThat(textsUnder(required("results-card")))
                .contains("Name review — results", "Changed (1)", "Well", "No target · other");
    }

    // IF the row carried no mark, THEN the person closing the card would lose track of what changed.
    @Test
    void review_cardClosed_theChangedRowKeepsAMarkerAndTheOtherDoesNot() throws Exception {
        showScreen();
        reviewTypingWell();

        onFx(() -> button("results-close").fire());

        assertThat(visibleMarkers("names-style-table")).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(
                        () -> required("names-style-changed-filter").isVisible()))
                .isTrue();
    }

    @Test
    void changedFilter_turnedOn_showsOnlyTheChangedRowsAndOffShowsAllAgain() throws Exception {
        showScreen();
        reviewTypingWell();
        onFx(() -> button("results-close").fire());
        final ToggleButton filter = (ToggleButton) required("names-style-changed-filter");

        onFx(filter::fire);
        assertThat(ThemeTestSupport.onFx(() -> glossaryTable().getItems()))
                .extracting(GlossaryEntry::term)
                .containsExactly("Well");

        onFx(filter::fire);
        assertThat(ThemeTestSupport.onFx(() -> glossaryTable().getItems())).hasSize(2);
    }

    // IF the button closed the card and left the table as it was, THEN "Show changed rows" would be a second Close.
    @Test
    void showChangedRows_pressed_closesTheCardAndFiltersTheTable() throws Exception {
        showScreen();
        reviewTypingWell();

        onFx(() -> button("results-show-changed").fire());

        assertThat(isCardShown()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> glossaryTable().getItems()))
                .extracting(GlossaryEntry::term)
                .containsExactly("Well");
        assertThat(ThemeTestSupport.onFx(() -> ((ToggleButton) required("names-style-changed-filter")).isSelected()))
                .isTrue();
    }

    // IF a marker outlived the next operation, THEN "changed in last run" would name rows of an older one.
    @Test
    void nextOperation_findsNothing_dropsTheMarkersAndTheFilter() throws Exception {
        showScreen();
        reviewTypingWell();
        onFx(() -> button("results-close").fire());
        glossary.willAnswer(Result.ok(List.of()));

        onFx(() -> button("names-style-model-scan").fire());
        awaitFx(this::isCardShown);

        assertThat(textsUnder(required("results-card"))).contains("Nothing changed.");
        assertThat(visibleMarkers("names-style-table")).isZero();
        assertThat(ThemeTestSupport.onFx(
                        () -> required("names-style-changed-filter").isVisible()))
                .isFalse();
    }

    // The same card opens for the recurring terms, with its own marker and filter.
    @Test
    void termScan_modelAddsATerm_opensTheCardAndMarksTheTermRow() throws Exception {
        showScreen();
        lexicon.modelWillFind(LexiconEntry.of("p1", "pentacle"));

        onFx(() -> button("recurring-model-scan").fire());
        awaitFx(this::isCardShown);

        assertThat(textsUnder(required("results-card"))).contains("Term scan — results", "Added (1)", "pentacle");
        onFx(() -> button("results-close").fire());
        assertThat(visibleMarkers("recurring-table")).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(
                        () -> required("recurring-changed-filter").isVisible()))
                .isTrue();
    }
}
