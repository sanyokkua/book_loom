package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;

/** Settings › Generation as the window builds it: the quality table and the fixed limits; it stores nothing. */
class SettingsGenerationScreenTest extends SettingsScreenTestBase {

    private final MemorySettings store = new MemorySettings();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.settings(store);
    }

    private void openGeneration() throws TimeoutException {
        openSettings();
        final Tab tab = tabs().getTabs().stream()
                .filter(each -> "settings-tab-generation".equals(each.getId()))
                .findFirst()
                .orElseThrow();
        onFx(() -> tabs().getSelectionModel().select(tab));
        awaitFx(() -> lookup("#generation-quality").tryQuery().isPresent());
    }

    private static boolean holdsInput(final Node node) {
        if (node instanceof Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(SettingsGenerationScreenTest::holdsInput)) {
            return true;
        }
        return node instanceof TextInputControl
                || node instanceof ComboBoxBase
                || node instanceof ButtonBase
                || node instanceof ToggleSwitch;
    }

    // Thinking and sampling are chosen per model on the Models tab (design D20), so Generation holds no setting at all:
    // only what each quality choice does and the fixed time limits, which are the rules the HTTP client enforces.
    @Test
    void generationTab_hasNoThinkingNoSampling_andFixedLimits() throws TimeoutException {
        openGeneration();

        assertThat(lookup("#generation-thinking").tryQuery()).isEmpty();
        assertThat(lookup("#generation-sampling").tryQuery()).isEmpty();
        assertThat(lookup("#generation-sampling-card").tryQuery()).isEmpty();
        assertThat(lookup("#generation-instant").tryQuery()).isEmpty();
        assertThat(lookup("#generation-running").tryQuery()).isEmpty();
        assertThat(text("generation-quality-same")).isEqualTo("The same for every model.");
        assertThat(text("generation-limit-helper")).isEqualTo("120 s (longer for a model that always thinks)");
        assertThat(text("generation-limit-draft"))
                .isEqualTo("0.5 s per expected token, at least the provider's request timeout, at most 600 s");
        assertThat(text("generation-limit-silence")).isEqualTo("60 s");
        assertThat(text("generation-limit-tries")).startsWith("up to 3 tries");
        assertThat(ThemeTestSupport.onFx(() -> holdsInput(required("generation-limits"))))
                .isFalse();
    }

    // IF a cell of the table could be edited, THEN the table would offer a choice the program does not have.
    @Test
    void qualityTable_opened_listsFastBalancedMaxAndNoEditableCell() throws TimeoutException {
        openGeneration();

        assertThat(List.of(
                        text("generation-quality-head-fast"),
                        text("generation-quality-head-balanced"),
                        text("generation-quality-head-max")))
                .containsExactly("Fast", "Balanced", "Max quality");
        assertThat(lookup("#generation-quality-batch-max").tryQuery()).isEmpty();
        assertThat(text("generation-quality-reviews-fast")).isEqualTo("0");
        assertThat(List.of(
                        text("generation-quality-proofread-fast"),
                        text("generation-quality-proofread-balanced"),
                        text("generation-quality-proofread-max")))
                .containsExactly(
                        "marks for review, no model calls", "marked passages", "marked passages, then the whole book");
        assertThat(text("generation-quality-label-proofread")).isEqualTo("Proofread after the last chapter");
        assertThat(lookup("#generation-quality-summary-max").tryQuery()).isEmpty();
        assertThat(text("generation-quality-repair-balanced")).isEqualTo("one repair");
        assertThat(text("generation-quality-repair-fast")).isEqualTo("lost marks only");
        assertThat(ThemeTestSupport.onFx(() -> holdsInput(required("generation-quality"))))
                .isFalse();
    }
}
