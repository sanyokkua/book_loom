package ua.bookloom.ui.screen;

import java.util.List;
import java.util.stream.Collectors;
import javafx.beans.value.ObservableBooleanValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The cells of the glossary table, one editing control each.
 *
 * <p>A cell is reused for other rows as the table scrolls, so each control is filled from the row it now shows while a
 * flag holds back its own change handler, and a typed target is remembered with the row it was typed for: a focus loss
 * that arrives after the cell moved on must not write the old text into the new row. Cells log nothing, since they are
 * refreshed on every scroll. A row's tooltips wait for the pointer's first visit, since most rows are never hovered.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryCells {

    private static final double BADGE_SPACING = 4;

    /** A cell holding one control that is filled from the row it shows. */
    abstract static class EntryCell<N extends Node> extends TableCell<GlossaryEntry, GlossaryEntry> {

        private final N control;
        private boolean syncing;

        EntryCell(final N control) {
            this.control = control;
            setText(null);
        }

        final N control() {
            return control;
        }

        final boolean isSyncing() {
            return syncing;
        }

        abstract void show(N shown, GlossaryEntry entry);

        @Override
        protected final void updateItem(final @Nullable GlossaryEntry entry, final boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            syncing = true;
            try {
                show(control, entry);
            } finally {
                syncing = false;
            }
            setGraphic(control);
        }
    }

    /**
     * The source term, and a meaning chip when the name's meaning is kept as a note; a changed row is marked by the
     * table's row edge, so nothing else shares the cell.
     */
    static final class TermCell extends EntryCell<HBox> {

        private final Messages messages;
        private final Label term = withFullText(new Label());
        private final Label meaning;

        TermCell(final Messages messages) {
            super(new HBox(BADGE_SPACING));
            this.messages = messages;
            term.getStyleClass().add("glossary-term");
            meaning = chip(messages, MessageKey.NAMES_STYLE_MEANING, MessageKey.NAMES_STYLE_MEANING_TIP);
            meaning.getStyleClass().add("glossary-meaning");
            control().setAlignment(Pos.CENTER_LEFT);
            control().getChildren().addAll(term, meaning);
        }

        // The cell may cut a long term; its whole text is the hover.
        private static Label withFullText(final Label term) {
            return Tips.installLive(term, term.textProperty());
        }

        @Override
        void show(final HBox shown, final GlossaryEntry entry) {
            term.setText(entry.term());
            final String note = entry.meaning();
            showIf(meaning, note != null);
            if (note != null) {
                Tips.rewordOnHover(meaning, messages.get(MessageKey.NAMES_STYLE_MEANING_TIP, note));
            }
        }
    }

    /** A small chip that explains itself on hover; shown only where a row has what it marks. */
    static Label chip(final Messages messages, final MessageKey text, final MessageKey tip) {
        final Label chip = Tips.installOnHover(messages, new Label(messages.get(text)), tip);
        chip.getStyleClass().addAll("chip", "glossary-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    static void showIf(final Node node, final boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /** The action that removes the row, in the table's last column. */
    static final class RemoveCell extends EntryCell<Button> {

        RemoveCell(final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
            super(Tips.installOnHover(messages, new Button("✕"), MessageKey.NAMES_STYLE_REMOVE_TIP));
            control().getStyleClass().addAll("btn-ghost", "glossary-remove");
            control().disableProperty().bind(frozen);
            control().setAccessibleText(messages.get(MessageKey.NAMES_STYLE_REMOVE));
            control().setOnAction(event -> {
                final GlossaryEntry shown = getItem();
                if (shown != null) {
                    model.remove(shown.id());
                }
            });
        }

        @Override
        void show(final Button shown, final GlossaryEntry entry) {
            // The button is the same for every row.
        }
    }

    /**
     * The target as a text field that writes on Enter and on losing focus, its origin chip (person, book, rule or
     * likely), the review flags the service computed for it, and for a suggestion a tick that accepts it. A target the
     * model suggested is slanted and its chip reads likely; Enter on an untouched suggestion accepts it too.
     */
    static final class TargetCell extends EntryCell<HBox> {

        private static final PseudoClass SUGGESTED = PseudoClass.getPseudoClass("suggested");

        private final Messages messages;
        private final NamesStyleViewModel model;
        private final TextField field;
        private final Label origin;
        private final Label flag;
        private final Button accept;
        private @Nullable String syncedId;
        private String syncedText = "";

        TargetCell(final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
            super(new HBox(BADGE_SPACING));
            this.messages = messages;
            this.model = model;
            field = Tips.installOnHover(messages, new TextField(), MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP);
            field.setPromptText(messages.get(MessageKey.NAMES_STYLE_NO_TARGET));
            origin = chip(messages, MessageKey.NAMES_STYLE_SUGGESTED_BADGE, MessageKey.NAMES_STYLE_ORIGIN_EXPLAINED);
            origin.getStyleClass().add("glossary-origin");
            flag = chip(messages, MessageKey.NAMES_STYLE_ISSUE_OBLIQUE_ENDING, MessageKey.NAMES_STYLE_FLAG_EXPLAINED);
            flag.getStyleClass().addAll("chip-warn", "glossary-review-flag");
            // A long flag gives way to the field; its whole words are the hover.
            flag.setMinWidth(Region.USE_COMPUTED_SIZE);
            accept = Tips.installOnHover(messages, new Button("✓"), MessageKey.NAMES_STYLE_ACCEPT_TIP);
            accept.getStyleClass().addAll("btn-ghost", "glossary-accept");
            accept.setAccessibleText(messages.get(MessageKey.NAMES_STYLE_ACCEPT));
            accept.setMinWidth(Region.USE_PREF_SIZE);
            accept.setOnAction(event -> acceptShown());
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setOnAction(event -> onEnter());
            field.focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    commit();
                }
            });
            control().setAlignment(Pos.CENTER_LEFT);
            control().getChildren().addAll(field, origin, flag, accept);
            control().disableProperty().bind(frozen);
        }

        private void onEnter() {
            final GlossaryEntry shown = getItem();
            if (shown != null && shown.isSuggested() && field.getText().equals(syncedText)) {
                acceptShown();
                return;
            }
            commit();
        }

        private void acceptShown() {
            final GlossaryEntry shown = getItem();
            if (shown != null && shown.id().equals(syncedId)) {
                model.accept(shown.id());
            }
        }

        private void commit() {
            final GlossaryEntry shown = getItem();
            final String typed = field.getText();
            if (shown == null || !shown.id().equals(syncedId) || typed.equals(syncedText)) {
                return;
            }
            syncedText = typed;
            model.setTarget(shown.id(), typed);
        }

        @Override
        void show(final HBox shown, final GlossaryEntry entry) {
            syncedId = entry.id();
            syncedText = entry.target() == null ? "" : entry.target();
            field.setText(syncedText);
            final boolean suggested = entry.isSuggested();
            field.pseudoClassStateChanged(SUGGESTED, suggested);
            showOrigin(entry, suggested);
            showFlags(model.reviewFlags().getOrDefault(entry.id(), List.of()));
            showIf(accept, suggested);
        }

        // A suggestion's chip says likely and how a suggestion is used; any other target's says who produced it.
        private void showOrigin(final GlossaryEntry entry, final boolean suggested) {
            final Origin from = entry.targetOrigin();
            showIf(origin, from != null);
            if (from == null) {
                return;
            }
            origin.setText(GlossaryLabels.origin(messages, from));
            origin.getStyleClass().remove("glossary-suggested-badge");
            if (suggested) {
                origin.getStyleClass().add("glossary-suggested-badge");
            }
            Tips.rewordOnHover(
                    origin,
                    messages.get(
                            suggested
                                    ? MessageKey.NAMES_STYLE_SUGGESTED_BADGE_TIP
                                    : MessageKey.NAMES_STYLE_ORIGIN_EXPLAINED));
        }

        private void showFlags(final List<TargetIssue> issues) {
            showIf(flag, !issues.isEmpty());
            if (issues.isEmpty()) {
                return;
            }
            final String words = issues.stream()
                    .map(issue -> GlossaryLabels.issue(messages, issue))
                    .distinct()
                    .collect(Collectors.joining(", "));
            flag.setText(words);
            Tips.rewordOnHover(flag, messages.get(MessageKey.NAMES_STYLE_FLAG_EXPLAINED, words));
        }
    }
}
