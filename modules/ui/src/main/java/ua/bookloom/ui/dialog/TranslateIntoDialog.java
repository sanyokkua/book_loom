package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.ProjectNameRules;

/**
 * The {@link TranslateIntoPrompt} shown as a card in the shell's modal host. A language the book already has is marked
 * and then needs a name of its own; for any other the name is optional and the automatic one is shown as the
 * placeholder.
 */
@Slf4j
@Singleton
public final class TranslateIntoDialog implements TranslateIntoPrompt {

    private static final String PREFIX = "translateinto-";
    private static final double ROW_GAP = 4;
    private static final double PAIR_GAP = 16;
    private static final double HALF = 50;

    private final ModalHost modalHost;
    private final Messages messages;
    private final LanguageNames languages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param languages names the languages in the list
     */
    @Inject
    public TranslateIntoDialog(final ModalHost modalHost, final Messages messages, final LanguageNames languages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    @Override
    public void ask(
            final BookGroup group,
            final ProjectNameRules rules,
            final BiConsumer<String, @Nullable String> create,
            final Runnable cancelled) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(cancelled, "cancelled");
        log.debug("asking which language to translate book {} into", group.contentHash());
        new Card(group, rules, create, cancelled).show();
    }

    /** One opening of the card and the little state its button depends on. */
    private final class Card {

        private final BookGroup group;
        private final ProjectNameRules rules;
        private final @Nullable String source;
        private final FormCard card;
        private final ComboBox<String> into = new ComboBox<>();
        private final NameField name;
        private final Label hint = FormCard.text("", "dialog-sub");
        private final Button create;

        Card(
                final BookGroup group,
                final ProjectNameRules rules,
                final BiConsumer<String, @Nullable String> onCreate,
                final Runnable cancelled) {
            this.group = group;
            this.rules = rules;
            this.source = group.rows().getFirst().summary().project().brief().sourceLanguage();
            this.card = new FormCard(
                    modalHost,
                    messages,
                    PREFIX,
                    ModalCard.Size.WIDE,
                    messages.get(MessageKey.DIALOG_TRANSLATE_INTO_TITLE));
            this.name = new NameField(messages, PREFIX, "name", this::automaticName, this::refresh);
            this.create = addButtons(onCreate, cancelled);
            card.onEscape(cancelled);
            configureList();
            card.body(
                    FormCard.text(messages.get(MessageKey.DIALOG_TRANSLATE_INTO_BODY, group.title()), "dialog-text"),
                    languagePair(),
                    name.view(messages.get(MessageKey.DIALOG_TRANSLATE_INTO_NAME)),
                    hint,
                    startFrom());
            into.valueProperty().addListener((observable, old, now) -> refresh());
            refresh();
        }

        private Button addButtons(final BiConsumer<String, @Nullable String> onCreate, final Runnable cancelled) {
            card.button(
                    PREFIX + "cancel",
                    messages.get(MessageKey.COMMON_CANCEL),
                    MessageKey.COMMON_CANCEL_TIP,
                    FormCard.Kind.SECONDARY,
                    cancelled);
            return card.button(
                    PREFIX + "create",
                    messages.get(MessageKey.DIALOG_TRANSLATE_INTO_CREATE),
                    MessageKey.DIALOG_TRANSLATE_INTO_CREATE_TIP,
                    FormCard.Kind.PRIMARY,
                    () -> {
                        final String typed = name.field().getText().strip();
                        onCreate.accept(
                                Objects.requireNonNull(into.getValue(), "target"), typed.isEmpty() ? null : typed);
                    });
        }

        void show() {
            card.show();
        }

        private void configureList() {
            final Locale locale = messages.locale();
            into.setId(PREFIX + "into");
            into.setPromptText(messages.get(MessageKey.DIALOG_TRANSLATE_INTO_INTO_PROMPT));
            into.setMaxWidth(Double.MAX_VALUE);
            Tips.install(messages, into, MessageKey.DIALOG_TRANSLATE_INTO_INTO_TIP);
            into.setItems(FXCollections.observableArrayList(languages.list(locale).stream()
                    .filter(tag -> !tag.equals(source))
                    .toList()));
            into.setConverter(new StringConverter<>() {
                @Override
                public String toString(final @Nullable String tag) {
                    return tag == null ? "" : listText(tag);
                }

                @Override
                public String fromString(final String text) {
                    return text;
                }
            });
        }

        private String listText(final String tag) {
            final String language = languages.listName(tag, messages.locale());
            final int used = usedCount(tag);
            return used == 0 ? language : messages.get(MessageKey.DIALOG_TRANSLATE_INTO_USED, language, used);
        }

        private int usedCount(final String tag) {
            return (int) group.rows().stream()
                    .filter(row -> tag.equals(row.summary().project().brief().targetLanguage()))
                    .count();
        }

        private Label fromLabel() {
            final Label from = new Label(
                    source == null
                            ? messages.get(MessageKey.DIALOG_TRANSLATE_INTO_FROM_AUTO)
                            : languages.nameOf(source, messages.locale()));
            from.setId(PREFIX + "from");
            return from;
        }

        private Node languagePair() {
            final GridPane pair = new GridPane();
            pair.setHgap(PAIR_GAP);
            final ColumnConstraints half = new ColumnConstraints();
            half.setPercentWidth(HALF);
            pair.getColumnConstraints().addAll(half, half);
            pair.add(labelled(MessageKey.DIALOG_TRANSLATE_INTO_INTO, into), 0, 0);
            pair.add(labelled(MessageKey.DIALOG_TRANSLATE_INTO_FROM, fromLabel()), 1, 0);
            return pair;
        }

        private Node labelled(final MessageKey caption, final Node control) {
            final Label label = new Label(messages.get(caption));
            label.getStyleClass().add("field-label");
            return new VBox(ROW_GAP, label, control);
        }

        private Node startFrom() {
            final Label caption = new Label(messages.get(MessageKey.DIALOG_TRANSLATE_INTO_START_FROM));
            caption.getStyleClass().add("field-label");
            return new VBox(
                    ROW_GAP,
                    caption,
                    FormCard.text(messages.get(MessageKey.DIALOG_TRANSLATE_INTO_START_FROM_BRIEF), "dialog-text"),
                    FormCard.text(messages.get(MessageKey.DIALOG_TRANSLATE_INTO_START_FROM_GLOSSARY), "dialog-text"));
        }

        private String automaticName() {
            final String target = into.getValue();
            return target == null ? "" : Project.automaticName(group.title(), source, target);
        }

        private void refresh() {
            final String target = into.getValue();
            final boolean used = target != null && usedCount(target) > 0;
            final String text = name.field().getText();
            final ProjectNameRules.Check check = rules.check(text);
            name.field().setPromptText(automaticName());
            name.refresh(check);
            hint.setText(messages.get(
                    used ? MessageKey.DIALOG_TRANSLATE_INTO_HINT_USED : MessageKey.DIALOG_TRANSLATE_INTO_HINT_AUTO));
            final boolean nameOk =
                    check == ProjectNameRules.Check.OK || (check == ProjectNameRules.Check.EMPTY && !used);
            create.setDisable(target == null || !nameOk);
        }
    }
}
