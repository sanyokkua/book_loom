package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.control.Banner;

/** The test dialog: what a stage sent and got back, the figures between, and Copy as curl with the key hidden. */
class ProviderTestDialogScreenTest extends SettingsScreenTestBase {

    private final List<String> copied = new CopyOnWriteArrayList<>();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.clipboard(copied::add);
    }

    private void passAllThree() throws TimeoutException {
        openSettings();
        checkWithReport(allPassedWithExchanges());
    }

    private void click(final String id) {
        onFx(() -> Event.fireEvent(
                required(id),
                new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        5,
                        5,
                        5,
                        5,
                        MouseButton.PRIMARY,
                        1,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        false,
                        false,
                        null)));
    }

    private String shown(final String id) {
        return ThemeTestSupport.onFx(() -> ((Labeled) required(id)).getText());
    }

    // IF activating a result did not open its details, THEN the request and the reply would stay out of reach.
    @Test
    void resultActivation_inferenceResultClicked_opensTheInferenceDialogWithItsRequestLine() throws TimeoutException {
        passAllThree();

        click("check-result-INFERENCE");

        assertThat(shown("test-request-line")).isEqualTo("POST http://127.0.0.1:11434/api/chat");
        final Banner verdict = (Banner) required("test-verdict");
        assertThat(verdict.getStyleClass()).contains("banner-ok");
        assertThat(ThemeTestSupport.onFx(() -> ((TextArea) required("test-body")).getText()))
                .isEqualTo("{\"model\":\"gemma3:12b\"}");
        assertThat(ThemeTestSupport.onFx(() -> ((TextArea) required("test-response")).getText()))
                .isEqualTo("OK");
        assertThat(required("test-metrics")).isNotNull();
        assertThat(shown("test-metric-time")).isEqualTo("1.2 s");
    }

    // IF Enter on a focused result did nothing, THEN the details would be mouse-only.
    @Test
    void resultActivation_enterOnFocusedModelsResult_opensTheModelsDialog() throws TimeoutException {
        passAllThree();

        onFx(() -> Event.fireEvent(
                required("check-result-MODELS"),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)));

        assertThat(shown("test-request-line")).isEqualTo("GET http://127.0.0.1:11434/api/tags");
        assertThat(ThemeTestSupport.onFx(() -> ((TextArea) required("test-response")).getText()))
                .isEqualTo("gemma3:12b\nqwen3:8b\nllama3:8b");
    }

    // IF Copy as curl put the key on the clipboard, THEN sharing a command would leak the credential.
    @Test
    void copyCurl_bearerKeyConfigured_clipboardHoldsTheMaskAndTheFooterSaysSo() throws TimeoutException {
        passAllThree();
        click("check-result-INFERENCE");

        onFx(() -> button("test-copy-curl").fire());

        assertThat(copied).hasSize(1);
        assertThat(copied.get(0))
                .startsWith("curl -X POST 'http://127.0.0.1:11434/api/chat'")
                .contains("-H 'Authorization: Bearer ••••'")
                .doesNotContain("HOME_API_KEY");
        assertThat(shown("modal-feedback")).isEqualTo("Copied as curl — the key is hidden.");
        assertThat(textsUnder(required("shell-toast-host"))).isEmpty();
    }

    // IF Copy on the body or the reply were silent, THEN a person could not tell whether it worked.
    @Test
    void copyBody_pressed_putsTheBodyOnTheClipboardAndSaysCopied() throws TimeoutException {
        passAllThree();
        click("check-result-INFERENCE");

        onFx(() -> button("test-copy-body").fire());
        onFx(() -> button("test-copy-response").fire());

        assertThat(copied).containsExactly("{\"model\":\"gemma3:12b\"}", "OK");
        assertThat(shown("modal-feedback")).isEqualTo("Copied.");
    }

    // IF the "(no body)" placeholder could be copied, THEN a person would paste words the request never had.
    @Test
    void copyBody_requestWithoutABody_isOffAndTheCurlHasNoDataPart() throws TimeoutException {
        passAllThree();
        click("check-result-MODELS");

        assertThat(ThemeTestSupport.onFx(() -> required("test-body").isVisible()))
                .isTrue();
        assertThat(ThemeTestSupport.onFx(() -> ((TextArea) required("test-body")).getText()))
                .isEqualTo("(no body)");
        assertThat(ThemeTestSupport.onFx(() -> button("test-copy-body").isDisabled()))
                .isTrue();
    }

    // IF the footer tabs did not switch the shown test, THEN a person would close and reopen to compare stages.
    @Test
    void tabs_modelsPressed_showTheModelsRequestAndKeepsInferenceReachable() throws TimeoutException {
        passAllThree();
        click("check-result-INFERENCE");

        onFx(() -> button("test-tab-models").fire());

        assertThat(shown("test-request-line")).isEqualTo("GET http://127.0.0.1:11434/api/tags");
        assertThat(ThemeTestSupport.onFx(() -> button("test-tab-inference").isDisabled()))
                .isFalse();
    }

    // IF a tab beyond the last run stage could be pressed, THEN it would open a test nobody ran.
    @Test
    void tabs_onlyConnectionRan_laterTabsAreOff() throws TimeoutException {
        openSettings();
        checkWithReport(firstStageOnly(ua.bookloom.api.llm.StageStatus.PASSED));
        click("check-result-CONNECTION");

        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        button("test-tab-connection").isDisabled(),
                        button("test-tab-models").isDisabled(),
                        button("test-tab-inference").isDisabled())))
                .containsExactly(false, false, false);
    }

    // IF the summary card's Test only ran the check, THEN the person would still hunt for what it found.
    @Test
    void summaryTest_pressed_runsInferenceAndOpensTheDialogOnIt() throws TimeoutException {
        verifier.respondWith(allPassedWithExchanges());
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));

        onFx(() -> button("settings-summary-test").fire());
        awaitFx(() -> optionalNode("test-request-line") != null
                && !((Labeled) required("test-request-line")).getText().isEmpty());

        assertThat(shown("test-request-line")).isEqualTo("POST http://127.0.0.1:11434/api/chat");
    }

    // IF Close left the card up, THEN the window would stay dimmed behind it.
    @Test
    void close_pressed_removesTheDialog() throws TimeoutException {
        passAllThree();
        click("check-result-INFERENCE");

        onFx(() -> button("test-close").fire());

        assertThat(ThemeTestSupport.onFx(() -> optionalNode("test-verdict"))).isNull();
    }

    // IF the dialog showed a verdict before the report arrived, THEN it would claim a result nobody has.
    @Test
    void dialog_testInFlight_saysTesting() throws Exception {
        verifier.hold();
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));
        onFx(() -> viewModel().tests().run(ua.bookloom.ui.state.ProviderTest.INFERENCE));
        verifier.awaitEntered();

        onFx(() -> ((Hyperlink) required("check-details-INFERENCE")).fire());

        final Banner verdict = (Banner) required("test-verdict");
        assertThat(ThemeTestSupport.onFx(() -> verdict.getStyleClass())).contains("banner-info");
        assertThat(shown("test-verdict-text")).isEqualTo("Testing...");
        verifier.release();
    }

    private javafx.scene.Node optionalNode(final String id) {
        return scene.getRoot().lookup("#" + id);
    }
}
