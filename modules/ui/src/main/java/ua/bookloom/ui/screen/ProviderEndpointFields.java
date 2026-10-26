package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/** Where the provider listens: the address and the two endpoint overrides, the second off for Ollama. */
final class ProviderEndpointFields {

    private final Messages messages;
    private final DraftText address;
    private final DraftText modelsPath;
    private final DraftText chatPath;
    private final Label addressWarning = new Label();
    private final Label chatNote = new Label();

    ProviderEndpointFields(final ProviderForm form, final Messages messages, final FormGrid grid) {
        this.messages = Objects.requireNonNull(messages, "messages");
        address = new DraftText(
                form,
                messages,
                "settings-form-address",
                "w-url",
                MessageKey.SETTINGS_FORM_ADDRESS_TIP,
                ProviderDraft::address,
                ProviderDraft::withAddress);
        modelsPath = new DraftText(
                form,
                messages,
                "settings-form-models-path",
                "w-name",
                MessageKey.SETTINGS_FORM_MODELS_PATH_TIP,
                ProviderDraft::modelsPath,
                ProviderDraft::withModelsPath);
        chatPath = new DraftText(
                form,
                messages,
                "settings-form-chat-path",
                "w-name",
                MessageKey.SETTINGS_FORM_CHAT_PATH_TIP,
                ProviderDraft::chatPath,
                ProviderDraft::withChatPath);
        grid.row(messages.get(MessageKey.SETTINGS_FORM_ADDRESS), addressBox());
        grid.row(messages.get(MessageKey.SETTINGS_FORM_MODELS_PATH), modelsPath.cell());
        grid.row(messages.get(MessageKey.SETTINGS_FORM_CHAT_PATH), chatBox());
    }

    private VBox addressBox() {
        addressWarning.setId("settings-form-address-warning");
        addressWarning.getStyleClass().add("hint-warn");
        addressWarning.setWrapText(true);
        return new VBox(4, address.cell(), addressWarning);
    }

    private VBox chatBox() {
        chatNote.setId("settings-form-chat-path-note");
        chatNote.setText(messages.get(MessageKey.SETTINGS_FORM_CHAT_PATH_NOTE));
        chatNote.getStyleClass().add("hint");
        chatNote.setWrapText(true);
        return new VBox(4, chatPath.cell(), chatNote);
    }

    void show(final ProviderDraft draft) {
        address.show(draft);
        modelsPath.show(draft);
        chatPath.show(draft);
        final boolean ollama = draft.kind() == ProviderKind.OLLAMA;
        chatPath.node().setDisable(ollama);
        chatNote.setVisible(ollama);
        chatNote.setManaged(ollama);
    }

    /** Shows the findings of the address and the chat endpoint under those fields. */
    void showErrors(final Function<ProviderDraft.Field, String> errorOf) {
        address.showError(errorOf.apply(ProviderDraft.Field.ADDRESS));
        chatPath.showError(errorOf.apply(ProviderDraft.Field.CHAT_PATH));
    }

    /** Shows the advice (not the errors) that belongs under the address. */
    void showAdvice(final List<ProviderDraft.FormFinding> findings) {
        final String advice = findings.stream()
                .filter(finding -> !finding.blocking() && finding.field() == ProviderDraft.Field.ADDRESS)
                .map(finding -> messages.get(finding.key(), finding.args().toArray()))
                .findFirst()
                .orElse("");
        addressWarning.setText(advice);
        addressWarning.setVisible(!advice.isEmpty());
        addressWarning.setManaged(!advice.isEmpty());
    }
}
