package ua.bookloom.ui.screen;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/** Builds the glossary table: Source term, Type, Target, Gender and Locked, each edited in its own cell. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryTable {

    private static final double ROW_HEIGHT = 46;
    private static final double SOURCE_WIDTH = 220;
    private static final double CHOICE_WIDTH = 140;
    private static final double TARGET_WIDTH = 260;
    private static final double LOCK_WIDTH = 100;

    static TableView<GlossaryEntry> build(final Messages messages, final NamesStyleViewModel model) {
        final TableView<GlossaryEntry> table = new TableView<>(model.rows());
        table.setId("names-style-table");
        table.getStyleClass().add("glossary-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label());
        table.setFocusTraversable(false);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().addAll(columns(messages, model));
        return table;
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> columns(
            final Messages messages, final NamesStyleViewModel model) {
        return Stream.concat(names(messages, model).stream(), attributes(messages, model).stream())
                .toList();
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> names(
            final Messages messages, final NamesStyleViewModel model) {
        return List.of(
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_SOURCE,
                        MessageKey.NAMES_STYLE_COLUMN_SOURCE_TIP,
                        SOURCE_WIDTH,
                        () -> new GlossaryCells.SourceCell(messages, model)),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_TYPE,
                        MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                        CHOICE_WIDTH,
                        () -> typeCell(messages, model)),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_TARGET,
                        MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP,
                        TARGET_WIDTH,
                        () -> new GlossaryCells.TargetCell(messages, model)));
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> attributes(
            final Messages messages, final NamesStyleViewModel model) {
        return List.of(
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_GENDER,
                        MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                        CHOICE_WIDTH,
                        () -> genderCell(messages, model)),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_LOCKED,
                        MessageKey.NAMES_STYLE_COLUMN_LOCKED_TIP,
                        LOCK_WIDTH,
                        () -> new GlossaryCells.LockCell(messages, model)));
    }

    private static GlossaryCells.EntryCell<?> typeCell(final Messages messages, final NamesStyleViewModel model) {
        return new GlossaryCells.ChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                TermType.values(),
                type -> GlossaryLabels.type(messages, type),
                GlossaryEntry::type,
                model::setType);
    }

    private static GlossaryCells.EntryCell<?> genderCell(final Messages messages, final NamesStyleViewModel model) {
        return new GlossaryCells.ChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                Gender.values(),
                gender -> GlossaryLabels.gender(messages, gender),
                GlossaryEntry::gender,
                model::setGender);
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> column(
            final Messages messages,
            final MessageKey caption,
            final MessageKey tip,
            final double width,
            final Supplier<GlossaryCells.EntryCell<?>> cell) {
        final TableColumn<GlossaryEntry, GlossaryEntry> column = new TableColumn<>(messages.get(caption));
        Tips.installOnHeader(messages, column, tip);
        column.setSortable(false);
        column.setReorderable(false);
        column.setPrefWidth(width);
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(_ -> cell.get());
        return column;
    }
}
