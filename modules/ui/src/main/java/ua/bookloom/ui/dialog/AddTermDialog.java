package ua.bookloom.ui.dialog;

import java.util.Objects;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.NewTerm;

/**
 * The Add term card: source, target, type, gender and lock, in the shape the reference gives every dialog.
 *
 * <p>The card stays open when the term is refused and says why beside the buttons, so the person corrects the field
 * instead of typing the term again. It never decides what is refused: the view model does, so the table's edits, an
 * import and this card cannot disagree.
 */
@Slf4j
public final class AddTermDialog {

    static final String CARD_ID = "add-term-card";
    private static final double FIELD_SPACING = 4;
    private static final double ROW_SPACING = 12;

    private final Messages messages;
    private final NamesStyleViewModel glossary;
    private final Runnable onClose;
    private final TextField source = field("add-term-source");
    private final TextField target = field("add-term-target");
    private final ComboBox<TermType> type;
    private final ComboBox<Gender> gender;
    private final ToggleSwitch lock;
    private final Label message = new Label();

    /**
     * Prepares the card's fields, empty; one instance backs one opening.
     *
     * @param messages the catalogue every word is drawn from
     * @param glossary what a term is added through
     * @param onClose what Cancel does, and what follows an added term
     */
    public AddTermDialog(final Messages messages, final NamesStyleViewModel glossary, final Runnable onClose) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.type = choice("add-term-type", TermType.values(), TermType.CHARACTER, this::typeLabel);
        this.gender = choice("add-term-gender", Gender.values(), Gender.UNKNOWN, this::genderLabel);
        this.lock = new ToggleSwitch(messages.get(MessageKey.DIALOG_ADD_TERM_LOCK));
        lock.setId("add-term-lock");
        message.setId("add-term-message");
        message.setWrapText(true);
        message.getStyleClass().add("dialog-sub");
        message.setVisible(false);
        message.setManaged(false);
    }

    /**
     * The card holding the fields.
     *
     * @return the card's root node, ready for {@code ModalHost.show}
     */
    public Node card() {
        final ModalCard card = new ModalCard(ModalCard.Size.WIDE);
        card.setId(CARD_ID);
        card.header(
                ModalCard.title(messages.get(MessageKey.DIALOG_ADD_TERM_TITLE)),
                ModalCard.subtitle(messages.get(MessageKey.DIALOG_ADD_TERM_SUBTITLE)));
        addTips();
        card.setContent(new VBox(
                ROW_SPACING,
                pair(
                        labelled(MessageKey.DIALOG_ADD_TERM_SOURCE, source),
                        labelled(MessageKey.DIALOG_ADD_TERM_TARGET, target)),
                pair(
                        labelled(MessageKey.DIALOG_ADD_TERM_TYPE, type),
                        labelled(MessageKey.DIALOG_ADD_TERM_GENDER, gender)),
                lock,
                message));
        buttons(card);
        return card;
    }

    private void buttons(final ModalCard card) {
        Tips.install(
                        messages,
                        card.button(
                                messages.get(MessageKey.DIALOG_ADD_TERM_CANCEL),
                                ButtonBar.ButtonData.CANCEL_CLOSE,
                                "add-term-cancel",
                                "btn-ghost"),
                        MessageKey.DIALOG_ADD_TERM_CANCEL_TIP)
                .setOnAction(event -> onClose.run());
        Tips.install(
                        messages,
                        card.button(
                                messages.get(MessageKey.DIALOG_ADD_TERM_CONFIRM),
                                ButtonBar.ButtonData.OK_DONE,
                                "add-term-confirm",
                                "btn-primary"),
                        MessageKey.DIALOG_ADD_TERM_CONFIRM_TIP)
                .setOnAction(event -> confirm());
    }

    private void addTips() {
        Tips.install(messages, source, MessageKey.DIALOG_ADD_TERM_SOURCE_TIP);
        Tips.install(messages, target, MessageKey.DIALOG_ADD_TERM_TARGET_TIP);
        Tips.install(messages, type, MessageKey.DIALOG_ADD_TERM_TYPE_TIP);
        Tips.install(messages, gender, MessageKey.DIALOG_ADD_TERM_GENDER_TIP);
        Tips.install(messages, lock, MessageKey.DIALOG_ADD_TERM_LOCK_TIP);
    }

    private void confirm() {
        final NewTerm term =
                new NewTerm(source.getText(), target.getText(), type.getValue(), gender.getValue(), lock.isSelected());
        glossary.add(term, this::added, this::refused);
    }

    private void added() {
        log.debug("the term was added: closing the card");
        onClose.run();
    }

    private void refused(final String reason) {
        log.debug("the term was refused: the card stays open");
        message.setText(reason);
        message.setVisible(true);
        message.setManaged(true);
    }

    private static TextField field(final String id) {
        final TextField field = new TextField();
        field.setId(id);
        return field;
    }

    private <T> ComboBox<T> choice(
            final String id, final T[] values, final T initial, final Function<T, String> label) {
        final ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setId(id);
        box.setValue(initial);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(final T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(final String text) {
                return initial;
            }
        });
        return box;
    }

    private String typeLabel(final TermType type) {
        return GlossaryLabels.type(messages, type);
    }

    private String genderLabel(final Gender gender) {
        return GlossaryLabels.gender(messages, gender);
    }

    private Node labelled(final MessageKey caption, final Region control) {
        final Label label = new Label(messages.get(caption));
        label.getStyleClass().add("field-label");
        control.setMaxWidth(Double.MAX_VALUE);
        final VBox column = new VBox(FIELD_SPACING, label, control);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    private static Node pair(final Node left, final Node right) {
        return new HBox(ROW_SPACING, left, right);
    }
}
