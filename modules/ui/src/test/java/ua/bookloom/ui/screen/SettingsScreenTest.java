package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The settings screen the application builds: its tabs, the providers area and the check control, all read from the
 * real scene. "Wired" is asserted behaviourally, by driving the view model and reading the node, and by firing the node
 * and reading what the recording verifier was asked.
 */
class SettingsScreenTest extends SettingsScreenTestBase {

    // IF a tab were missing, or one of the four unbuilt areas were enabled, THEN the screen would misstate what this
    // build can do.
    @Test
    void tabs_settingsScreen_sixPresentFourDisabled() {
        openSettings();

        assertThat(tabs().getTabs())
                .extracting(Tab::getId, Tab::getText, Tab::isDisable)
                .containsExactly(
                        tuple("settings-tab-providers", "Providers", false),
                        tuple("settings-tab-models", "Models", true),
                        tuple("settings-tab-generation", "Generation", true),
                        tuple("settings-tab-appearance", "Appearance", false),
                        tuple("settings-tab-automation", "Automation", true),
                        tuple("settings-tab-storage", "Storage & logs", true));
    }

    // IF a provider action were missing or usable, THEN a person could reach an editor this build does not have.
    @ParameterizedTest
    @ValueSource(strings = {"add", "edit", "delete"})
    void providerActions_settingsScreen_addEditDeletePresentAndDisabled(final String action) {
        openSettings();

        assertThat(required("settings-provider-" + action))
                .isInstanceOfSatisfying(
                        Button.class, button -> assertThat(button.isDisable()).isTrue());
    }

    // IF a third row appeared, or the order changed, THEN the list would not be the two built-in local servers.
    @Test
    void providerList_settingsScreen_listsOllamaThenLmStudioOnly() {
        openSettings();

        assertThat(((Pane) required("settings-provider-list")).getChildren())
                .extracting(Node::getId)
                .containsExactly("provider-row-ollama", "provider-row-lmstudio");
    }

    // IF a row named the wrong server or showed another endpoint, THEN the person would check a server they did not
    // mean.
    @ParameterizedTest
    @CsvSource({
        "ollama, Ollama, http://localhost:11434",
        "lmstudio, LM Studio, http://localhost:1234/v1",
    })
    void providerRow_settingsScreen_showsItsNameAndEndpoint(final String id, final String name, final String endpoint) {
        openSettings();

        final Node row = required("provider-row-" + id);

        assertThat(row.lookup(".provider-name"))
                .isInstanceOfSatisfying(
                        Label.class, label -> assertThat(label.getText()).isEqualTo(name));
        assertThat(row.lookup(".provider-endpoint"))
                .isInstanceOfSatisfying(
                        Label.class, label -> assertThat(label.getText()).isEqualTo(endpoint));
    }

    // IF the selection mark did not follow the model, THEN the list would show a different provider from the one a
    // check is aimed at.
    @Test
    void providerRow_clicked_selectsIt() {
        openSettings();
        assertThat(required("provider-row-ollama").getStyleClass()).contains("list-item-selected");
        assertThat(required("provider-row-lmstudio").getStyleClass()).doesNotContain("list-item-selected");

        onFx(() -> required("provider-row-lmstudio").fireEvent(click()));

        assertThat(ThemeTestSupport.onFx(() -> viewModel().selectedProviderId().get()))
                .isEqualTo("lmstudio");
        assertThat(required("provider-row-lmstudio").getStyleClass()).contains("list-item-selected");
        assertThat(required("provider-row-ollama").getStyleClass()).doesNotContain("list-item-selected");
    }

    // IF the check were offered before a model is chosen, THEN a person could fire it against nothing.
    @Test
    void checkControl_noModel_presentAndDisabled() {
        openSettings();

        assertThat(required("settings-check")).isInstanceOfSatisfying(Button.class, button -> {
            assertThat(button.getText()).isEqualTo("Check provider");
            assertThat(button.isDisable()).isTrue();
        });
    }

    // IF the control were not wired to the view model, THEN choosing a model would not enable it, and firing it would
    // not ask the verifier about the selected provider and model.
    @Test
    void checkControl_modelSet_enabledAndFiringItCallsTheVerifierOnce() throws InterruptedException {
        openSettings();
        onFx(() -> viewModel().model().set(MODEL));
        final Button button = (Button) required("settings-check");
        assertThat(button.isDisable()).isFalse();

        onFx(button::fire);
        verifier.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verifier.callCount()).isEqualTo(1);
        assertThat(verifier.selections()).containsExactly(new ModelSelection("ollama", MODEL));
    }

    // IF the in-progress line were shown or laid out when nothing is running, THEN it would claim work that is not
    // happening; and if it stayed after the report, it would never go away.
    @Test
    void progress_whileChecking_visibleAndManaged_thenHidden() throws InterruptedException, TimeoutException {
        openSettings();
        final Label progress = (Label) required("settings-check-progress");
        assertThat(progress.isVisible()).isFalse();
        assertThat(progress.isManaged()).isFalse();
        verifier.respondWith(firstStageOnly(StageStatus.PASSED));
        verifier.hold();

        onFx(() -> {
            viewModel().model().set(MODEL);
            viewModel().check();
        });
        verifier.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(progress.isVisible()).isTrue();
        assertThat(progress.isManaged()).isTrue();
        assertThat(progress.getText()).isEqualTo("Checking…");
        assertThat(((Button) required("settings-check")).isDisable()).isTrue();

        verifier.release();
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> !ThemeTestSupport.onFx(progress::isVisible));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(progress.isVisible()).isFalse();
        assertThat(progress.isManaged()).isFalse();
    }
    // IF a label were still English in Ukrainian, THEN the screen would be half translated.
    @Test
    void screenInUkrainian_labelsDifferFromEnglish() {
        openSettings();
        final String englishCheck = ((Button) required("settings-check")).getText();
        final List<String> englishTabs =
                tabs().getTabs().stream().map(Tab::getText).toList();

        useLocale(Locale.forLanguageTag("uk"));
        openSettings();
        final String ukrainianCheck = ((Button) required("settings-check")).getText();
        final List<String> ukrainianTabs =
                tabs().getTabs().stream().map(Tab::getText).toList();

        assertThat(ukrainianCheck).isNotBlank().isNotEqualTo(englishCheck);
        assertThat(ukrainianTabs)
                .hasSize(6)
                .allSatisfy(title -> assertThat(title).isNotBlank());
        assertThat(ukrainianTabs).doesNotContainAnyElementsOf(englishTabs);
    }

    private static MouseEvent click() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                4,
                4,
                4,
                4,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
    }
}
