package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * Shows what one provider test sent and got back, over the settings screen. The card follows the test runner: a request
 * shows as soon as it is sent, the reply and the figures fill in when they arrive, and it stays open until closed;
 * nothing in it is saved.
 */
@Slf4j
@Singleton
public final class ProviderTestDialog {

    private final ModalHost modalHost;
    private final Messages messages;
    private final SettingsViewModel viewModel;
    private final TextClipboard clipboard;
    private final LeaveDialog leave;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param viewModel the settings state, whose test runner the card follows
     * @param clipboard where Copy puts text
     * @param leave the question asked when the card is closed while its test runs
     */
    @Inject
    public ProviderTestDialog(
            final ModalHost modalHost,
            final Messages messages,
            final SettingsViewModel viewModel,
            final TextClipboard clipboard,
            final LeaveDialog leave) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.leave = Objects.requireNonNull(leave, "leave");
    }

    /**
     * Opens the card on one test. FX thread only.
     *
     * @param stage the test to show first; the footer switches to the others that have run
     */
    public void open(final VerificationStage stage) {
        Objects.requireNonNull(stage, "stage");
        log.debug("opening the test dialog on {}", stage);
        final ProviderTestView view =
                new ProviderTestView(messages, viewModel, clipboard, stage, modalHost::hide, leave::askCloseTest);
        modalHost.show(view.card(), false, true);
    }
}
