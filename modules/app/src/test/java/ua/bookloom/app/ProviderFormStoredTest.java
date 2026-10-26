package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/**
 * The Providers form in the real window over the real SQLite file: what it refuses is marked at its own field, Save
 * explains itself and stores nothing, and an emptied provider list stays empty after a restart.
 */
// `slow`: the whole window over a database file, once per case.
@Tag("slow")
class ProviderFormStoredTest extends WorkspaceTestBase {

    // A port nothing listens on: a save re-reads the model list, which must fail fast and never reach a real program.
    private static final String ADDRESS = "http://127.0.0.1:9/v1";

    @ParameterizedTest
    @ValueSource(strings = {"sk-literal-SECRET-abc123", "sk_live_51HabcDEF123ghiJKL456", "my key"})
    void save_keyTypedAsTheVariable_isRefusedAtTheFieldAndNothingIsStored(final String typed) throws Exception {
        startStoredWindow();
        newProvider("Cloud", ADDRESS);
        onFx(() -> ((ToggleButton) node("#settings-form-auth-bearer")).fire());
        type("#settings-form-key-variable", typed);

        pressSave();

        assertThat(errorUnder("#settings-form-key-variable"))
                .isEqualTo("Type the NAME of an environment variable that holds the key, not the key itself.");
        assertThat(saveTip()).startsWith("Fix the marked fields first:");
        assertThat(stored()).isZero();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "http://user:pass@127.0.0.1:11434|Leave the user name and password out of the address: it is stored and shown in clear.",
                "http://127.0.0.1:0|Use a port from 1 to 65535.",
                "http://127.0.0.1:65536|Use a port from 1 to 65535.",
                "http://127.0.0.1:-1|Enter an address that starts with http:// or https://.",
                "127.0.0.1|Enter an address that starts with http:// or https://.",
                "localhost:11434|Enter an address that starts with http:// or https://.",
                "ftp://127.0.0.1/v1|Enter an address that starts with http:// or https://.",
                "http://local host|Enter an address that starts with http:// or https://."
            })
    void save_unusableAddress_isRefusedAtTheAddress(final String address, final String finding) throws Exception {
        startStoredWindow();
        newProvider("Local", address);

        pressSave();

        assertThat(errorUnder("#settings-form-address")).isEqualTo(finding);
        assertThat(stored()).isZero();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "0|Enter a whole number of seconds above zero.",
                "-1|Enter a whole number of seconds above zero.",
                "1.5|Enter a whole number of seconds above zero.",
                "abc|Enter a whole number of seconds above zero.",
                "''|Enter a whole number of seconds above zero.",
                "99999999999|Enter at most 3600 seconds (one hour).",
                "3601|Enter at most 3600 seconds (one hour)."
            })
    void save_unusableTimeout_isRefusedAtTheTimeout(final String seconds, final String finding) throws Exception {
        startStoredWindow();
        newProvider("Local", ADDRESS);
        type("#settings-form-connect-timeout", seconds);

        pressSave();

        assertThat(errorUnder("#settings-form-connect-timeout")).isEqualTo(finding);
        assertThat(stored()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"X Api", "X-Api:", "Ключ"})
    void save_headerNameWithAForbiddenCharacter_isRefusedUnderTheHeaders(final String header) throws Exception {
        startStoredWindow();
        newProvider("Local", ADDRESS);
        addHeader(header);

        pressSave();

        assertThat(errorUnder("#settings-form-headers")).startsWith("The header name " + header);
        assertThat(stored()).isZero();
    }

    @Test
    void save_headerNameOfTwoHundredCharacters_isRefusedUnderTheHeaders() throws Exception {
        startStoredWindow();
        newProvider("Local", ADDRESS);
        addHeader("X-" + "a".repeat(198));

        pressSave();

        assertThat(errorUnder("#settings-form-headers")).startsWith("The header name X-aaa");
        assertThat(stored()).isZero();
    }

    @Test
    void restart_afterEveryProviderWasDeleted_keepsTheListEmpty() throws Exception {
        startStoredWindow();
        newProvider("Local", ADDRESS);
        pressSave();
        assertThat(stored()).isEqualTo(1);
        final String id =
                injector.getInstance(ProviderConfigs.class).all().get(0).id();
        onFx(() -> injector.getInstance(ProviderForm.class).delete(id, () -> {}));
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> stored() == 0);

        startStoredWindow();

        assertThat(stored()).isZero();
    }

    private void newProvider(final String name, final String address) throws Exception {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        waitUntil(() -> shell.root().lookup("#settings-provider-add") != null);
        onFx(() -> ((Button) node("#settings-provider-add")).fire());
        type("#settings-form-name", name);
        type("#settings-form-address", address);
    }

    // The switch and "+ Add" only make an empty row; the row's name field edits the draft exactly like this.
    private void addHeader(final String name) {
        onFx(() -> injector.getInstance(ProviderForm.class)
                .edit(draft -> draft.withHeaders(List.of(new ProviderDraft.HeaderRow(name, "1")))));
    }

    private void type(final String selector, final String text) {
        onFx(() -> ((TextField) node(selector)).setText(text));
    }

    private void pressSave() throws Exception {
        onFx(() -> ((Button) node("#settings-form-save")).fire());
        waitUntil(() -> !injector.getInstance(ProviderForm.class).saving().get());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String errorUnder(final String field) {
        return onFx(() -> {
            final Label error = (Label) node(field + "-error");
            return error.isVisible() ? error.getText() : "";
        });
    }

    private String saveTip() {
        return onFx(() -> Objects.requireNonNull(((Button) node("#settings-form-save")).getTooltip())
                .getText());
    }

    private int stored() {
        return injector.getInstance(ProviderConfigs.class).all().size();
    }

    private Node node(final String selector) {
        return Objects.requireNonNull(shell.root().lookup(selector), selector);
    }
}
