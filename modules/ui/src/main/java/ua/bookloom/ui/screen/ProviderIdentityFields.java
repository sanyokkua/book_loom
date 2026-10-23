package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/** The first rows of the connection details: the name, the kind, and how requests sign in. */
final class ProviderIdentityFields {

    private final ProviderForm form;
    private final Messages messages;
    private final DraftText name;
    private final KeyFields keys;
    private final ComboBox<ProviderKind> kind = new ComboBox<>();
    private final ToggleButton none = new ToggleButton();
    private final ToggleButton bearer = new ToggleButton();
    private final ToggleButton apiKey = new ToggleButton();
    private final Label keyStatus = new Label();
    private final FormGrid.Row keyHeaderRow;
    private final FormGrid.Row keyVariableRow;

    ProviderIdentityFields(final ProviderForm form, final Messages messages, final FormGrid grid) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        name = new DraftText(
                form,
                messages,
                "settings-form-name",
                "w-name",
                MessageKey.SETTINGS_FORM_NAME_TIP,
                ProviderDraft::name,
                ProviderDraft::withName);
        keys = new KeyFields(form, messages);
        grid.row(messages.get(MessageKey.SETTINGS_FORM_NAME), name.node());
        grid.row(messages.get(MessageKey.SETTINGS_KIND_LABEL), kindCombo());
        grid.row(messages.get(MessageKey.SETTINGS_FORM_AUTH), authButtons());
        keyHeaderRow = grid.row(messages.get(MessageKey.SETTINGS_FORM_KEY_HEADER), keys.header.node());
        keyVariableRow = grid.row(messages.get(MessageKey.SETTINGS_FORM_KEY_VARIABLE), keyVariableBox());
    }

    /** The two texts that say where the key comes from. */
    private static final class KeyFields {
        private final DraftText header;
        private final DraftText variable;

        KeyFields(final ProviderForm form, final Messages messages) {
            header = new DraftText(
                    form,
                    messages,
                    "settings-form-key-header",
                    "w-name",
                    MessageKey.SETTINGS_FORM_KEY_HEADER_TIP,
                    ProviderDraft::apiKeyHeader,
                    ProviderDraft::withApiKeyHeader);
            variable = new DraftText(
                    form,
                    messages,
                    "settings-form-key-variable",
                    "w-name",
                    MessageKey.SETTINGS_FORM_KEY_VARIABLE_TIP,
                    ProviderDraft::credentialRef,
                    ProviderDraft::withCredentialRef);
        }
    }

    private ComboBox<ProviderKind> kindCombo() {
        kind.setId("settings-form-kind");
        kind.getStyleClass().add("w-name");
        kind.getItems().setAll(ProviderKind.values());
        kind.setConverter(new StringConverter<>() {
            @Override
            public String toString(final ProviderKind value) {
                return value == null
                        ? ""
                        : messages.get(
                                value == ProviderKind.OLLAMA
                                        ? MessageKey.SETTINGS_KIND_OLLAMA
                                        : MessageKey.SETTINGS_KIND_OPENAI);
            }

            @Override
            public ProviderKind fromString(final String text) {
                throw new UnsupportedOperationException("the kind is picked, never typed");
            }
        });
        Tips.install(messages, kind, MessageKey.SETTINGS_KIND_LABEL_TIP);
        kind.valueProperty().addListener((observed, was, now) -> {
            if (now != null) {
                form.edit(draft -> draft.withKind(now));
            }
        });
        return kind;
    }

    private SegmentedButton authButtons() {
        label(
                none,
                "settings-form-auth-none",
                MessageKey.SETTINGS_FORM_AUTH_NONE,
                MessageKey.SETTINGS_FORM_AUTH_NONE_TIP);
        label(
                bearer,
                "settings-form-auth-bearer",
                MessageKey.SETTINGS_FORM_AUTH_BEARER,
                MessageKey.SETTINGS_FORM_AUTH_BEARER_TIP);
        label(
                apiKey,
                "settings-form-auth-apikey",
                MessageKey.SETTINGS_FORM_AUTH_APIKEY,
                MessageKey.SETTINGS_FORM_AUTH_APIKEY_TIP);
        final SegmentedButton segmented = new SegmentedButton(none, bearer, apiKey);
        segmented.setId("settings-form-auth");
        segmented.getToggleGroup().selectedToggleProperty().addListener((observed, was, now) -> onAuthPicked(now));
        return segmented;
    }

    private void label(final ToggleButton button, final String id, final MessageKey text, final MessageKey tip) {
        button.setId(id);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setText(messages.get(text));
        Tips.install(messages, button, tip);
    }

    // A toggle in a group deselects when pressed again; the sign-in has not changed then, so the choice must still
    // show.
    private void onAuthPicked(final Toggle picked) {
        if (picked == null) {
            showAuth(form.draft().get().auth());
        } else {
            form.edit(draft -> draft.withAuth(authOf(picked)));
        }
    }

    private ProviderAuth authOf(final Toggle toggle) {
        if (bearer.equals(toggle)) {
            return ProviderAuth.BEARER;
        }
        return apiKey.equals(toggle) ? ProviderAuth.API_KEY : ProviderAuth.NONE;
    }

    private void showAuth(final ProviderAuth auth) {
        final ToggleButton wanted =
                switch (auth) {
                    case NONE -> none;
                    case BEARER -> bearer;
                    case API_KEY -> apiKey;
                };
        if (!wanted.isSelected()) {
            wanted.setSelected(true);
        }
    }

    private VBox keyVariableBox() {
        keyStatus.setId("settings-form-key-status");
        final Label hint = new Label(messages.get(MessageKey.SETTINGS_FORM_KEY_VARIABLE_HINT));
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        return new VBox(4, new HBox(8, keys.variable.node(), keyStatus), hint);
    }

    void show(final ProviderDraft draft) {
        name.show(draft);
        keys.header.show(draft);
        keys.variable.show(draft);
        if (kind.getValue() != draft.kind()) {
            kind.setValue(draft.kind());
        }
        showAuth(draft.auth());
        keyHeaderRow.show(draft.auth() == ProviderAuth.API_KEY);
        keyVariableRow.show(draft.auth() != ProviderAuth.NONE);
    }

    /** Shows whether the key's variable has a value, in words; never the value. */
    void showCredential(final ProviderDraft.CredentialStatus status) {
        final boolean shown = status != ProviderDraft.CredentialStatus.NONE;
        keyStatus.setVisible(shown);
        keyStatus.setManaged(shown);
        keyStatus.getStyleClass().removeAll("key-status-set", "key-status-unset");
        if (shown) {
            final boolean isSet = status == ProviderDraft.CredentialStatus.SET;
            keyStatus.setText(
                    messages.get(isSet ? MessageKey.SETTINGS_FORM_KEY_SET : MessageKey.SETTINGS_FORM_KEY_NOT_SET));
            keyStatus.getStyleClass().add(isSet ? "key-status-set" : "key-status-unset");
        }
    }
}
