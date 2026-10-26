package ua.bookloom.ui.screen;

import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelForm;

/**
 * What the chosen model refused: one row per setting with the day, what BookLoom did instead and Send again, or a
 * plain "Nothing refused" with the reason it can happen. Send again acts at once and leaves the unsaved typing alone.
 */
final class ModelRefusedSection {

    private static final String OMITTED = "omitted";

    private final ModelForm form;
    private final Messages messages;
    private final VBox box = new VBox(8);
    private final VBox rows = new VBox(0);
    private final VBox none = new VBox(4);
    private final Label failed = new Label();

    ModelRefusedSection(final ModelForm form, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        final Label title = new Label(messages.get(MessageKey.SETTINGS_MODELS_REFUSED));
        title.getStyleClass().add("card-title");
        final Label sub = new Label(messages.get(MessageKey.SETTINGS_MODELS_REFUSED_SUB));
        sub.getStyleClass().add("muted");
        rows.setId("models-refused");
        none.getChildren()
                .addAll(
                        label("models-refused-none", MessageKey.SETTINGS_MODELS_REFUSED_NONE, "muted"),
                        label("models-refused-none-hint", MessageKey.SETTINGS_MODELS_REFUSED_NONE_HINT, "hint"));
        failed.setId("models-refused-error");
        failed.setText(messages.get(MessageKey.SETTINGS_MODELS_REFUSED_SEND_FAILED));
        failed.getStyleClass().add("hint-warn");
        box.getChildren().addAll(new HBox(8, title, sub), rows, none, failed);
        show();
    }

    VBox node() {
        return box;
    }

    /** Draws the list as the form holds it, and the note of a Send again that failed. */
    void show() {
        rows.getChildren().setAll(form.refused().stream().map(this::row).toList());
        final boolean empty = form.refused().isEmpty();
        none.getChildren().forEach(label -> Visibility.show(label, empty));
        Visibility.show(none, empty);
        Visibility.show(rows, !empty);
        Visibility.show(failed, form.refusedFailed().get());
    }

    private HBox row(final RefusedSetting refused) {
        final String name = refused.name();
        final Label setting = new Label(name);
        setting.setId("models-refused-name-" + name);
        setting.getStyleClass().add("tech-note");
        final Label why = new Label(why(refused));
        why.setId("models-refused-why-" + name);
        why.setWrapText(true);
        why.getStyleClass().add("hint");
        HBox.setHgrow(why, Priority.ALWAYS);
        why.setMaxWidth(Double.MAX_VALUE);
        final Button send = new Button(messages.get(MessageKey.SETTINGS_MODELS_REFUSED_SEND));
        send.setId("models-refused-send-" + name);
        send.getStyleClass().add("btn-ghost");
        send.disableProperty().bind(form.sendingAgain());
        Tips.install(messages, send, MessageKey.SETTINGS_MODELS_REFUSED_SEND_TIP);
        send.setOnAction(event -> form.sendAgain(name));
        final HBox row = new HBox(10, setting, why, send);
        row.setId("models-refused-row-" + name);
        row.getStyleClass().add("refused-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String why(final RefusedSetting refused) {
        // The ICU date argument is a java.util.Date, formatted in the JVM's zone, so the day is handed over as that
        // zone's midnight (as LastOpened does).
        final Date day =
                Date.from(refused.at().atStartOfDay(ZoneId.systemDefault()).toInstant());
        return OMITTED.equals(refused.insteadDid())
                ? messages.get(MessageKey.SETTINGS_MODELS_REFUSED_OMITTED, day)
                : messages.get(MessageKey.SETTINGS_MODELS_REFUSED_INSTEAD, day, refused.insteadDid());
    }

    private Label label(final String id, final MessageKey text, final String style) {
        final Label label = new Label(messages.get(text));
        label.setId(id);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        return label;
    }
}
