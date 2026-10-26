package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/**
 * The {@link RenamePrompt} shown as a card in the shell's modal host.
 *
 * <p>Rename stays disabled until the name is non-empty, differs from the current one (or is the automatic name again)
 * and is not used by another project of the book. "Use the automatic name again" sends {@code null} so the name follows
 * the languages once more; any edit after it makes the text the person's own.
 */
@Slf4j
@Singleton
public final class RenameDialog implements RenamePrompt {

    private static final String PREFIX = "rename-";
    private static final double SPACING = 8;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public RenameDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final ProjectRow row,
            final ProjectNameRules rules,
            final @Nullable String error,
            final @Nullable String typed,
            final Consumer<@Nullable String> rename,
            final Runnable remove,
            final Runnable cancelled) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(rename, "rename");
        Objects.requireNonNull(remove, "remove");
        Objects.requireNonNull(cancelled, "cancelled");
        log.debug(
                "asking for a new name of project {}, error shown: {}",
                row.summary().project().id(),
                error != null);
        new Card(row, rules, error, typed, rename, remove, cancelled).show();
    }

    /** One opening of the card and the little state its buttons depend on. */
    private final class Card {

        private final String current;
        private final ProjectNameRules rules;
        private final FormCard card;
        private final NameField name;
        private final Button confirm;
        private boolean automatic;
        private boolean filling;

        Card(
                final ProjectRow row,
                final ProjectNameRules rules,
                final @Nullable String error,
                final @Nullable String typed,
                final Consumer<@Nullable String> rename,
                final Runnable remove,
                final Runnable cancelled) {
            this.rules = rules;
            this.current = row.summary().project().name();
            final boolean auto = row.summary().project().nameOrigin() == NameOrigin.AUTO;
            this.card = new FormCard(
                    modalHost,
                    messages,
                    PREFIX + "card",
                    ModalCard.Size.DIALOG,
                    messages.get(MessageKey.DIALOG_RENAME_TITLE));
            this.name = new NameField(messages, PREFIX, "field", () -> current, this::edited);
            this.confirm = addButtons(rename, remove, cancelled);
            card.onEscape(cancelled);
            card.body(
                    Wrapped.label(
                            messages.get(
                                    auto ? MessageKey.DIALOG_RENAME_BODY_AUTO : MessageKey.DIALOG_RENAME_BODY_NAMED),
                            "dialog-text"),
                    name.view(messages.get(MessageKey.DIALOG_PROJECT_NAME_FIELD)),
                    useAutomatic(row));
            name.start(typed == null ? current : typed);
            name.showServiceError(error);
            refresh();
        }

        private Button addButtons(
                final Consumer<@Nullable String> rename, final Runnable remove, final Runnable cancelled) {
            card.button(
                    PREFIX + "remove",
                    messages.get(MessageKey.DIALOG_RENAME_REMOVE),
                    MessageKey.DIALOG_RENAME_REMOVE_TIP,
                    ButtonBar.ButtonData.LEFT,
                    "btn-ghost",
                    remove);
            card.button(
                    PREFIX + "cancel",
                    messages.get(MessageKey.COMMON_CANCEL),
                    MessageKey.COMMON_CANCEL_TIP,
                    ButtonBar.ButtonData.CANCEL_CLOSE,
                    "btn-secondary",
                    cancelled);
            return card.button(
                    PREFIX + "confirm",
                    messages.get(MessageKey.DIALOG_RENAME_CONFIRM),
                    MessageKey.DIALOG_RENAME_CONFIRM_TIP,
                    ButtonBar.ButtonData.OK_DONE,
                    "btn-primary",
                    () -> rename.accept(
                            automatic ? null : name.field().getText().strip()));
        }

        void show() {
            card.show();
            FormCard.focusWhenShown(name.field(), true);
        }

        private HBox useAutomatic(final ProjectRow row) {
            final Button use = new Button(messages.get(MessageKey.DIALOG_RENAME_USE_AUTO));
            use.setId(PREFIX + "use-auto");
            use.getStyleClass().add("btn-secondary");
            Tips.install(messages, use, MessageKey.DIALOG_RENAME_USE_AUTO_TIP);
            use.setOnAction(event -> useAutomaticName());
            final Label note = Wrapped.label(messages.get(MessageKey.DIALOG_RENAME_USE_AUTO_NOTE), "dialog-sub");
            final boolean offered = row.summary().project().nameOrigin() == NameOrigin.USER
                    && rules.pairFree()
                    && rules.automaticName() != null;
            use.setMinWidth(Region.USE_PREF_SIZE);
            note.setWrapText(true);
            final HBox line = new HBox(SPACING, use, note);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setVisible(offered);
            line.setManaged(offered);
            use.setVisible(offered);
            return line;
        }

        private void useAutomaticName() {
            final String auto = Objects.requireNonNull(rules.automaticName(), "automaticName");
            log.debug("the automatic name is used again");
            filling = true;
            name.field().setText(auto);
            filling = false;
            automatic = true;
            refresh();
        }

        private void edited() {
            if (!filling) {
                automatic = false;
                refresh();
            }
        }

        private void refresh() {
            final String text = name.field().getText().strip();
            final ProjectNameRules.Check check = rules.check(text);
            name.refresh(check);
            final boolean usable = check == ProjectNameRules.Check.OK && (automatic || !text.equals(current));
            confirm.setDisable(!usable);
            log.debug("rename card refreshed: check={} automatic={} usable={}", check, automatic, usable);
        }
    }
}
