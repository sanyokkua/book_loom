package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.dialog.ConfirmDialog;

/** The question asked before a provider is deleted: it names the provider and Cancel is the safe default. */
class DeleteProviderQuestionTest extends ShellTestBase {

    private final List<String> deleted = new ArrayList<>();

    private void ask() {
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(ConfirmDialog.class)
                    .ask(
                            ConfirmDialog.Question.DELETE_PROVIDER,
                            List.of("Office LM"),
                            List.of("Office LM"),
                            () -> deleted.add("Office LM"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the question did not name the provider, THEN a person could delete the wrong one.
    @Test
    void ask_deleteProvider_namesTheProviderAndExplainsTheConsequence() {
        ask();

        assertThat(((Label) scene.getRoot().lookup("#confirm-card .dialog-title")).getText())
                .isEqualTo("Delete “Office LM”?");
        assertThat(((Label) scene.getRoot().lookup("#confirm-card .dialog-text")).getText())
                .contains("Office LM itself is not touched");
        assertThat(((Button) required("confirm-yes")).getText()).isEqualTo("Delete");
        assertThat(((Button) required("confirm-cancel")).isDefaultButton()).isTrue();
    }

    // IF Cancel deleted, THEN the safe answer would not be safe.
    @Test
    void cancel_deleteProvider_deletesNothing() {
        ask();

        ThemeTestSupport.onFx(() -> {
            ((Button) required("confirm-cancel")).fire();
            return null;
        });

        assertThat(deleted).isEmpty();
    }

    // IF Delete did nothing, THEN the question would be a dead end.
    @Test
    void confirm_deleteProvider_runsTheDeletion() {
        ask();

        ThemeTestSupport.onFx(() -> {
            ((Button) required("confirm-yes")).fire();
            return null;
        });

        assertThat(deleted).containsExactly("Office LM");
    }
}
