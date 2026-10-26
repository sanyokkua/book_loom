package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.QualityLabels;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/**
 * The {@link SamePairPrompt} shown as a card in the shell's modal host. A second translation is the default, because
 * the card opens from an attempt to make one; the name button stays disabled until the name is free.
 */
@Slf4j
@Singleton
public final class SamePairDialog implements SamePairPrompt {

    private static final String PREFIX = "samepair-";
    private static final String SEPARATOR = " · ";
    private static final double GAP = 12;
    private static final double NOTE_GAP = 2;
    private static final double ROW_GAP = 4;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public SamePairDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final ProjectRow existing,
            final String languageName,
            final String keepName,
            final ProjectNameRules rules,
            final Runnable openExisting,
            final Consumer<String> makeSecond,
            final Runnable cancelled) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(languageName, "languageName");
        Objects.requireNonNull(keepName, "keepName");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(openExisting, "openExisting");
        Objects.requireNonNull(makeSecond, "makeSecond");
        Objects.requireNonNull(cancelled, "cancelled");
        log.debug(
                "asking about a second {} translation of project {}",
                languageName,
                existing.summary().project().id());
        new Card(existing, languageName, keepName, rules, openExisting, makeSecond, cancelled).show();
    }

    /** One opening of the card and the little state its button depends on. */
    private final class Card {

        private final ProjectNameRules rules;
        private final String languageName;
        private final FormCard card;
        private final NameField name;
        private final RadioButton open = new RadioButton();
        private final RadioButton second = new RadioButton();
        private final Button use;
        private final VBox nameBox;

        Card(
                final ProjectRow existing,
                final String languageName,
                final String keepName,
                final ProjectNameRules rules,
                final Runnable openExisting,
                final Consumer<String> makeSecond,
                final Runnable cancelled) {
            this.rules = rules;
            this.languageName = languageName;
            this.card = new FormCard(
                    modalHost,
                    messages,
                    PREFIX,
                    ModalCard.Size.WIDE,
                    messages.get(MessageKey.DIALOG_SAME_PAIR_TITLE, languageName));
            final String automatic = rules.automaticName();
            final String base = automatic == null ? existing.summary().project().name() : automatic;
            this.name = new NameField(messages, PREFIX, "name", () -> base, this::refresh);
            name.field().setPromptText(messages.get(MessageKey.DIALOG_SAME_PAIR_NAME_PLACEHOLDER, base));
            this.use = addButtons(keepName, openExisting, makeSecond, cancelled);
            card.onEscape(cancelled);
            this.nameBox = nameSection();
            card.body(content(existing));
            final ToggleGroup group = new ToggleGroup();
            open.setToggleGroup(group);
            second.setToggleGroup(group);
            group.selectedToggleProperty().addListener((observable, old, now) -> refresh());
            second.setSelected(true);
            refresh();
        }

        private Button addButtons(
                final String keepName,
                final Runnable openExisting,
                final Consumer<String> makeSecond,
                final Runnable cancelled) {
            card.button(
                    PREFIX + "cancel",
                    keepName,
                    MessageKey.DIALOG_SAME_PAIR_KEEP_TIP,
                    FormCard.Kind.SECONDARY,
                    cancelled);
            return card.button(PREFIX + "use", "", MessageKey.DIALOG_SAME_PAIR_USE_TIP, FormCard.Kind.PRIMARY, () -> {
                if (open.isSelected()) {
                    openExisting.run();
                } else {
                    makeSecond.accept(name.field().getText().strip());
                }
            });
        }

        private Node[] content(final ProjectRow existing) {
            return new Node[] {
                Wrapped.label(messages.get(MessageKey.DIALOG_SAME_PAIR_BODY), "dialog-text"),
                table(existing),
                choice(
                        open,
                        "open",
                        MessageKey.DIALOG_SAME_PAIR_OPEN,
                        MessageKey.DIALOG_SAME_PAIR_OPEN_NOTE,
                        MessageKey.DIALOG_SAME_PAIR_OPEN_TIP,
                        new Object[0]),
                choice(
                        second,
                        "second",
                        MessageKey.DIALOG_SAME_PAIR_SECOND,
                        MessageKey.DIALOG_SAME_PAIR_SECOND_NOTE,
                        MessageKey.DIALOG_SAME_PAIR_SECOND_TIP,
                        new Object[] {languageName}),
                nameBox
            };
        }

        void show() {
            card.show();
            FormCard.focusWhenShown(name.field(), false);
        }

        private VBox nameSection() {
            final Label hint = Wrapped.label(messages.get(MessageKey.DIALOG_SAME_PAIR_NAME_HINT), "dialog-sub");
            return new VBox(ROW_GAP, name.view(messages.get(MessageKey.DIALOG_SAME_PAIR_NAME_LABEL)), hint);
        }

        private Node choice(
                final RadioButton radio,
                final String id,
                final MessageKey caption,
                final MessageKey note,
                final MessageKey tip,
                final Object[] arguments) {
            radio.setId(PREFIX + id);
            radio.setText(messages.get(caption, arguments));
            Tips.install(messages, radio, tip);
            final Label noteLabel = Wrapped.label(messages.get(note), "dialog-sub");
            return new VBox(NOTE_GAP, radio, noteLabel);
        }

        private GridPane table(final ProjectRow existing) {
            final GridPane grid = new GridPane();
            grid.setHgap(GAP);
            grid.setVgap(ROW_GAP);
            final ColumnConstraints wide = new ColumnConstraints();
            wide.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(wide, wide, new ColumnConstraints());
            final MessageKey[] heads = {
                MessageKey.DIALOG_SAME_PAIR_COL_PROJECT,
                MessageKey.DIALOG_SAME_PAIR_COL_MODEL,
                MessageKey.DIALOG_SAME_PAIR_COL_PROGRESS
            };
            for (int column = 0; column < heads.length; column++) {
                final Label head = new Label(messages.get(heads[column]));
                head.getStyleClass().add("field-label");
                grid.add(head, column, 0);
            }
            grid.add(new Label(existing.summary().project().name()), 0, 1);
            grid.add(new Label(modelLine(existing)), 1, 1);
            grid.add(new Label(progress(existing)), 2, 1);
            return grid;
        }

        private String modelLine(final ProjectRow existing) {
            final RunRecord run = existing.summary().latestRun();
            final ModelSelection model = run == null ? null : run.model();
            final List<String> parts = new ArrayList<>();
            if (model != null) {
                if (!existing.providerName().isBlank()) {
                    parts.add(existing.providerName());
                }
                parts.add(model.modelId());
            }
            parts.add(messages.get(QualityLabels.of(existing.summary().project().brief())));
            return String.join(SEPARATOR, parts);
        }

        private String progress(final ProjectRow existing) {
            return existing.summary().latestRun() == null
                    ? messages.get(MessageKey.DIALOG_SAME_PAIR_NOT_STARTED)
                    : messages.get(MessageKey.DIALOG_SAME_PAIR_PROGRESS, existing.percent());
        }

        private void refresh() {
            final boolean opening = open.isSelected();
            final ProjectNameRules.Check check = rules.check(name.field().getText());
            name.refresh(check);
            nameBox.setDisable(opening);
            use.setText(
                    opening
                            ? messages.get(MessageKey.DIALOG_SAME_PAIR_OPEN)
                            : messages.get(MessageKey.DIALOG_SAME_PAIR_USE, languageName));
            use.setDisable(!opening && check != ProjectNameRules.Check.OK);
        }
    }
}
