package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.AutoNamesFixture.HALE;
import static ua.bookloom.ui.AutoNamesFixture.TABLE;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * Names &amp; style while "Prepare names automatically" runs in the background: the screen is read-only and says so,
 * each editing control is off, what only reads or leaves the screen stays on, and the screen is editable again, with
 * no banner, when the pass ends.
 */
class NamesStyleHiddenScreenTest extends NamesStyleAutoScreenTestBase {

    static final String BANNER = "Names is running in the background";

    private static final GlossaryEntry SUGGESTED = new GlossaryEntry(
                    "e1", "p1", "Well", null, TermType.CHARACTER, Gender.MALE, false)
            .withSuggestedTarget("Велл");
    private static final GlossaryEntry LOCKED =
            new GlossaryEntry("e4", "p1", "Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, true);

    private void showWithSuggestion() throws Exception {
        readyToStart();
        lexicon.holds(TABLE);
        glossary.willAnswer(Result.ok(List.of(SUGGESTED, HALE, LOCKED)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> glossaryTable().getItems().size() == 3
                && !recurringTable().getItems().isEmpty());
        showAtSceneSize(1920, 700);
    }

    private void hideThePass() throws Exception {
        passWillSucceed();
        namesPass.hold();
        pressAuto();
        assertThat(namesPass.awaitStarted()).isTrue();
        awaitFx(this::isBusyCardShown);
        onFx(() -> button("progress-background").fire());
        awaitFx(() -> !isBusyCardShown());
    }

    private boolean isBannerShown() {
        final Node banner = optional("names-style-hidden-banner");
        return ThemeTestSupport.onFx(() -> banner != null && banner.isVisible() && banner.isManaged());
    }

    private boolean isDisabled(final String id) {
        return ThemeTestSupport.onFx(() -> required(id).isDisabled());
    }

    private List<Node> inTable(final String tableId, final String selector) {
        return ThemeTestSupport.onFx(() -> List.copyOf(required(tableId).lookupAll(selector)).stream()
                .filter(node -> node.getScene() != null)
                .toList());
    }

    // IF a control stayed live, THEN a person could edit a row the pass is about to rewrite.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "names-style-add",
                "recurring-add",
                "names-style-scan",
                "recurring-scan",
                "names-style-review",
                "recurring-review",
                "names-style-translate",
                "recurring-translate",
                "names-style-import",
                "recurring-import",
                "names-style-auto",
                "names-style-accept-all"
            })
    void hiddenPass_running_disablesTheEditingControl(final String id) throws Exception {
        showWithSuggestion();

        hideThePass();

        assertThat(isDisabled(id)).as(id).isTrue();
    }

    // IF reading stopped with editing, THEN the person could not look up a name while the pass runs.
    @ParameterizedTest
    @ValueSource(strings = {"names-style-search", "recurring-search", "names-style-export", "recurring-export"})
    void hiddenPass_running_keepsReadingAndExportingOn(final String id) throws Exception {
        showWithSuggestion();

        hideThePass();

        assertThat(isDisabled(id)).as(id).isFalse();
    }

    @Test
    void hiddenPass_running_keepsBackOn() throws Exception {
        showWithSuggestion();

        hideThePass();

        assertThat(isDisabled("names-style-back")).isFalse();
    }

    // IF a cell stayed live, THEN a typed translation would race the pass's single write.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "names-style-table|.text-field",
                "names-style-table|.toggle-switch",
                "names-style-table|.glossary-remove",
                "names-style-table|.glossary-accept",
                "recurring-table|.text-field",
                "recurring-table|.button"
            })
    void hiddenPass_running_disablesEveryRowControl(final String tableAndSelector) throws Exception {
        showWithSuggestion();

        hideThePass();

        final String[] parts = tableAndSelector.split("\\|", 2);
        assertThat(inTable(parts[0], parts[1]))
                .as(tableAndSelector)
                .isNotEmpty()
                .allMatch(Node::isDisabled);
    }

    // IF the Kind and Gender cells stayed live, THEN a click could change a name's type under the pass.
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void hiddenPass_running_disablesTheKindAndGenderCells(final int column) throws Exception {
        showWithSuggestion();

        hideThePass();

        assertThat(ThemeTestSupport.onFx(() -> glossaryTable().lookupAll(".table-row-cell").stream()
                        .map(node -> (TableRow<?>) node)
                        .filter(row -> row.getItem() != null)
                        .map(row ->
                                (TableCell<?, ?>) row.getChildrenUnmodifiable().get(column))
                        .map(cell -> cell.getGraphic().isDisabled())
                        .toList()))
                .isNotEmpty()
                .containsOnly(true);
    }

    // IF the screen went read-only with no word, THEN the person would think the window had frozen.
    @Test
    void hiddenPass_running_showsTheReadOnlyBannerWithItsWords() throws Exception {
        showWithSuggestion();

        hideThePass();

        assertThat(isBannerShown()).isTrue();
        assertThat(textsUnder(required("names-style-hidden-banner"))).contains(BANNER);
    }

    @Test
    void hiddenPass_notHidden_showsNoBannerAndLeavesTheControlsAlone() throws Exception {
        showWithSuggestion();

        assertThat(isBannerShown()).isFalse();
        assertThat(isDisabled("names-style-add")).isFalse();
    }

    // IF the freeze outlived the pass, THEN the screen would stay read-only with nothing running.
    @Test
    void hiddenPass_ends_enablesThemAgainAndHidesTheBanner() throws Exception {
        showWithSuggestion();
        hideThePass();

        namesPass.release();

        awaitFx(() -> !isDisabled("names-style-add"));
        assertThat(isDisabled("recurring-add")).isFalse();
        assertThat(isDisabled("names-style-import")).isFalse();
        assertThat(isBannerShown()).isFalse();
        assertThat(inTable("names-style-table", ".text-field")).isNotEmpty().noneMatch(Node::isDisabled);
    }
}
