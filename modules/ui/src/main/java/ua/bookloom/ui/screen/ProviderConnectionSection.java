package ua.bookloom.ui.screen;

import java.util.List;
import javafx.scene.Node;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/** All the aligned rows of the connection details: identity and sign-in, endpoints, and the two timeouts. */
final class ProviderConnectionSection {

    private final FormGrid grid = new FormGrid();
    private final ProviderIdentityFields identity;
    private final ProviderEndpointFields endpoints;
    private final DraftText connect;
    private final DraftText request;

    ProviderConnectionSection(final ProviderForm form, final Messages messages) {
        identity = new ProviderIdentityFields(form, messages, grid);
        endpoints = new ProviderEndpointFields(form, messages, grid);
        connect = new DraftText(
                form,
                messages,
                "settings-form-connect-timeout",
                "w-num",
                MessageKey.SETTINGS_FORM_CONNECT_TIMEOUT_TIP,
                ProviderDraft::connectSeconds,
                ProviderDraft::withConnectSeconds);
        request = new DraftText(
                form,
                messages,
                "settings-form-request-timeout",
                "w-num",
                MessageKey.SETTINGS_FORM_REQUEST_TIMEOUT_TIP,
                ProviderDraft::requestSeconds,
                ProviderDraft::withRequestSeconds);
        grid.row(messages.get(MessageKey.SETTINGS_FORM_CONNECT_TIMEOUT), connect.node());
        grid.row(messages.get(MessageKey.SETTINGS_FORM_REQUEST_TIMEOUT), request.node());
    }

    Node node() {
        return grid;
    }

    /** Shows the draft: texts, picks, and which rows apply to this kind and sign-in. */
    void show(final ProviderDraft draft) {
        identity.show(draft);
        endpoints.show(draft);
        connect.show(draft);
        request.show(draft);
    }

    void showCredential(final ProviderDraft.CredentialStatus status) {
        identity.showCredential(status);
    }

    void showAdvice(final List<ProviderDraft.FormFinding> findings) {
        endpoints.showAdvice(findings);
    }
}
