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
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ProviderTest;

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

    // IF a row named the wrong server or showed another host, THEN the person would check a server they did not
    // mean; the badge says which one is in use.
    @ParameterizedTest
    @CsvSource({
        "ollama, Ollama, localhost:11434, current",
        "lmstudio, LM Studio, localhost:1234, idle",
    })
    void providerRow_settingsScreen_showsNameHostAndBadge(
            final String id, final String name, final String hostPort, final String badge) {
        openSettings();

        final Node row = required("provider-row-" + id);

        assertThat(row.lookup(".provider-name"))
                .isInstanceOfSatisfying(
                        Label.class, label -> assertThat(label.getText()).isEqualTo(name));
        assertThat(row.lookup(".provider-endpoint"))
                .isInstanceOfSatisfying(
                        Label.class, label -> assertThat(label.getText()).isEqualTo(hostPort));
        assertThat(row.lookup(".chip"))
                .isInstanceOfSatisfying(
                        Label.class, label -> assertThat(label.getText()).isEqualTo(badge));
    }

    // IF the card showed a listing that was never made, or hid the one that was, THEN the person could not tell what
    // the server offers.
    @Test
    void detailCard_beforeAnyListing_showsKindEndpointAndSaysNoListingWasMade() {
        openSettings();

        assertThat(label("settings-detail-kind").getText()).isEqualTo("Ollama");
        assertThat(label("settings-detail-endpoint").getText()).isEqualTo("http://localhost:11434");
        assertThat(label("settings-detail-models").getText()).isEqualTo("No listing has been made yet");
    }

    @Test
    void detailCard_afterAListing_showsTheCountAndTheFirstThreeNames() throws TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b", "mistral-small:24b", "phi4:14b");

        openSettingsSettled();

        assertThat(label("settings-detail-models").getText()).isEqualTo("4 · gemma3:12b, qwen3:8b, mistral-small:24b");
    }

    @Test
    void detailCard_lmStudioSelected_showsItsKindAndFullEndpoint() {
        openSettings();

        onFx(() -> viewModel().selectProvider("lmstudio"));

        assertThat(label("settings-detail-kind").getText()).isEqualTo("OpenAI-compatible");
        assertThat(label("settings-detail-endpoint").getText()).isEqualTo("http://localhost:1234/v1");
        assertThat(label("settings-detail-models").getText()).isEqualTo("No listing has been made yet");
    }

    // IF the chooser were drawn on another area, THEN two controls would edit one choice.
    @Test
    void modelChooser_settingsScreen_isOnTheProvidersCardOnly() {
        openSettings();

        assertThat(required("settings-detail-card").lookup("#settings-model")).isNotNull();
        assertThat(tab("settings-tab-appearance").getContent().lookup("#settings-model"))
                .isNull();
    }

    // IF the subtitle were missing, THEN the screen would not state the promise the application keeps.
    @Test
    void subtitle_settingsScreen_statesEverythingIsLocal() {
        openSettings();

        assertThat(label("settings-subtitle").getText()).isEqualTo("Everything is local. Nothing leaves your machine.");
    }

    // IF the selected tab were not marked by an underline of the primary role, or another tab were, THEN the tab
    // strip would not match the reference rendering.
    @Test
    void tabs_providersSelected_onlyItsHeaderIsUnderlined() {
        openSettings();

        assertThat(underline("settings-tab-providers")).isEqualTo(Color.web("#a58075"));
        assertThat(underline("settings-tab-appearance")).isEqualTo(Color.TRANSPARENT);
        assertThat(underline("settings-tab-models")).isEqualTo(Color.TRANSPARENT);
    }

    // IF Appearance did not open, or Generation opened, THEN the tabs would misstate what this build can do.
    @Test
    void tabs_appearanceActivated_opensItAndGenerationLeavesProvidersShown() {
        openSettings();

        clickOn("#settings-tab-appearance");
        assertThat(selectedTabId()).isEqualTo("settings-tab-appearance");

        clickOn("#settings-tab-providers");
        clickOn("#settings-tab-generation");

        assertThat(selectedTabId()).isEqualTo("settings-tab-providers");
    }

    private String selectedTabId() {
        return ThemeTestSupport.onFx(
                () -> tabs().getSelectionModel().getSelectedItem().getId());
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

    // IF Test connection waited for a model, or the other two did not, THEN a person could not ask whether anything
    // is listening first, or could fire a model test against nothing.
    @Test
    void testControls_noModel_connectionAvailableOthersPresentAndDisabled() {
        openSettings();

        assertThat(required("settings-test-connection")).isInstanceOfSatisfying(Button.class, button -> {
            assertThat(button.getText()).isEqualTo("Test connection");
            assertThat(button.isDisable()).isFalse();
        });
        assertThat(required("settings-test-models")).isInstanceOfSatisfying(Button.class, button -> {
            assertThat(button.getText()).isEqualTo("Test models");
            assertThat(button.isDisable()).isTrue();
        });
        assertThat(required("settings-test-inference")).isInstanceOfSatisfying(Button.class, button -> {
            assertThat(button.getText()).isEqualTo("Test inference");
            assertThat(button.isDisable()).isTrue();
        });
    }

    // IF Test connection asked about a model, THEN it would be impossible before one is chosen.
    @Test
    void testConnection_noModel_callsTheConnectionOnlyPort() throws InterruptedException {
        openSettings();

        onFx(((Button) required("settings-test-connection"))::fire);
        verifier.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verifier.connectionCalls()).containsExactly("ollama");
        assertThat(verifier.selections()).isEmpty();
    }

    // IF a control were not wired to the view model, THEN choosing a model would not enable it, and firing it would
    // not ask the verifier about the selected provider and model with the depth it names.
    @ParameterizedTest
    @CsvSource({
        "settings-test-models, CONNECTION_AND_MODELS",
        "settings-test-inference, FULL",
    })
    void testControl_modelSet_enabledAndFiringItAsksForItsDepth(final String id, final VerificationPolicy policy)
            throws InterruptedException {
        openSettings();
        onFx(() -> viewModel().model().set(MODEL));
        final Button button = (Button) required(id);
        assertThat(button.isDisable()).isFalse();

        onFx(button::fire);
        verifier.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verifier.callCount()).isEqualTo(1);
        assertThat(verifier.selections()).containsExactly(new ModelSelection("ollama", MODEL));
        assertThat(verifier.policies()).containsExactly(policy);
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
            viewModel().tests().run(ProviderTest.INFERENCE);
        });
        verifier.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(progress.isVisible()).isTrue();
        assertThat(progress.isManaged()).isTrue();
        assertThat(progress.getText()).isEqualTo("Checking…");
        assertThat(((Button) required("settings-test-connection")).isDisable()).isTrue();

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
        final String englishCheck = ((Button) required("settings-test-connection")).getText();
        final List<String> englishTabs =
                tabs().getTabs().stream().map(Tab::getText).toList();

        useLocale(Locale.forLanguageTag("uk"));
        openSettings();
        final String ukrainianCheck = ((Button) required("settings-test-connection")).getText();
        final List<String> ukrainianTabs =
                tabs().getTabs().stream().map(Tab::getText).toList();

        assertThat(ukrainianCheck).isNotBlank().isNotEqualTo(englishCheck);
        assertThat(ukrainianTabs)
                .hasSize(6)
                .allSatisfy(title -> assertThat(title).isNotBlank());
        assertThat(ukrainianTabs).doesNotContainAnyElementsOf(englishTabs);
    }

    private Label label(final String id) {
        return (Label) required(id);
    }

    private Tab tab(final String id) {
        return tabs().getTabs().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow();
    }

    /** The bottom edge colour of a tab's header: the underline when it is the primary role, transparent otherwise. */
    private Paint underline(final String tabId) {
        return ThemeTestSupport.onFx(() -> ((Region) tabs().lookup("#" + tabId))
                .getBorder()
                .getStrokes()
                .get(0)
                .getBottomStroke());
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
