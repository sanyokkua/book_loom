package ua.bookloom.ui.screen;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The Forms cell of the glossary table: the other forms the book writes the name in, separated by commas, with how many
 * the entry joins on hover (the cell's own text holds the forms, so a cut list is read by widening the column), and the Unsure mark when some of its mentions also fit another name and the clause around
 * them did not decide which. Read-only; a cell logs nothing, since it is refreshed on every scroll.
 */
final class GlossaryFormsCell extends GlossaryCells.EntryCell<HBox> {

    private static final double SPACING = 4;
    private static final String SEPARATOR = ", ";

    private final Messages messages;
    private final Label forms = new Label();
    private final Label unsure;

    GlossaryFormsCell(final Messages messages) {
        super(new HBox(SPACING));
        this.messages = messages;
        forms.getStyleClass().add("glossary-forms");
        Tips.installOnHover(messages, forms, MessageKey.NAMES_STYLE_COLUMN_FORMS_TIP);
        HBox.setHgrow(forms, Priority.ALWAYS);
        unsure = GlossaryCells.chip(messages, MessageKey.NAMES_STYLE_UNSURE, MessageKey.NAMES_STYLE_UNSURE_TIP);
        unsure.getStyleClass().addAll("chip-warn", "glossary-unsure");
        control().setAlignment(Pos.CENTER_LEFT);
        control().getChildren().addAll(forms, unsure);
    }

    @Override
    void show(final HBox shown, final GlossaryEntry entry) {
        final String text = String.join(SEPARATOR, entry.forms());
        forms.setText(text);
        if (!entry.forms().isEmpty()) {
            Tips.rewordOnHover(
                    forms,
                    messages.get(
                            MessageKey.NAMES_STYLE_FORMS_JOINED, entry.forms().size()));
        }
        GlossaryCells.showIf(unsure, entry.unsureMentions() > 0);
    }
}
