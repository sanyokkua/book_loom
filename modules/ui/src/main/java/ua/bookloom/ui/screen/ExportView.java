package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The export screen's content: the report of the file a finished run wrote, or the statement that none exists yet,
 * beside the extra outputs that are shown and unavailable.
 *
 * <p>The screen reports and never writes: the run wrote the book as its last stage, so the only control that acts is
 * the one that shows the file in the system file manager. The extra outputs are drawn switched off, as the brief's unused cards are,
 * because the screen is specified to show them and nothing produces them.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportView {

    private static final double SCREEN_SPACING = 14;
    private static final double CARD_SPACING = 8;
    private static final double TILE_SPACING = 12;
    private static final double COLUMN_SPACING = 14;
    private static final double AUX_WIDTH = 320;

    /** The screen for a finished run: its file's format, path and counts, and the reveal action. */
    static Node populated(final JobReport report, final Path written, final Messages messages, final Runnable reveal) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(reveal, "reveal");
        log.debug("building the export report for {} accepted and {} flagged", report.accepted(), report.flagged());
        final Label hint = BriefCards.hint(messages, MessageKey.EXPORT_FORMAT_HINT);
        final Node card = BriefCards.card(
                "export-card",
                messages,
                MessageKey.EXPORT_CARD_TITLE,
                false,
                ImportViews.keyValue(
                        "export-format",
                        messages.get(MessageKey.EXPORT_FORMAT_LABEL),
                        messages.get(ImportViews.formatName(report.format()))),
                hint,
                ImportViews.keyValue("export-path", messages.get(MessageKey.EXPORT_PATH_LABEL), written.toString()),
                tiles(report, messages),
                revealButton(messages, reveal));
        final Label subtitle = ImportViews.wrapped(messages.get(MessageKey.EXPORT_SUBTITLE), "muted");
        subtitle.setId("export-subtitle");
        return screen(card, messages, subtitle);
    }

    /** The screen for no finished book: the statement that none exists, and nothing to reveal. */
    static Node empty(final Messages messages) {
        Objects.requireNonNull(messages, "messages");
        log.debug("building the export screen's empty state");
        final Label title = new Label(messages.get(MessageKey.EXPORT_EMPTY_TITLE));
        title.getStyleClass().add("kv-value");
        final Label text = ImportViews.wrapped(messages.get(MessageKey.EXPORT_EMPTY_TEXT), "muted");
        final VBox message = new VBox(CARD_SPACING, title, text);
        message.setId("export-empty");
        return screen(BriefCards.card("export-card", messages, MessageKey.EXPORT_CARD_TITLE, false, message), messages);
    }

    private static Node screen(final Node report, final Messages messages, final Node... lead) {
        final VBox left = new VBox(COLUMN_SPACING, report);
        HBox.setHgrow(left, Priority.ALWAYS);
        final HBox columns = new HBox(COLUMN_SPACING, left, auxiliary(messages));
        final VBox screen = new VBox(SCREEN_SPACING, lead);
        screen.getChildren().add(columns);
        return screen;
    }

    private static Node tiles(final JobReport report, final Messages messages) {
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final HBox row = new HBox(
                TILE_SPACING,
                tile("accepted", grouping.format(report.accepted()), MessageKey.TRANSLATING_COUNT_ACCEPTED, messages),
                tile("flagged", grouping.format(report.flagged()), MessageKey.TRANSLATING_COUNT_FLAGGED, messages));
        row.setPadding(new Insets(CARD_SPACING, 0, 0, 0));
        return row;
    }

    private static Node tile(final String name, final String count, final MessageKey caption, final Messages messages) {
        final Label number = new Label(count);
        number.setId("export-count-" + name);
        number.getStyleClass().add("stat-number");
        return TranslatingView.tile("export-" + name, number, messages.get(caption));
    }

    private static Button revealButton(final Messages messages, final Runnable reveal) {
        final Button button = new Button(messages.get(MessageKey.EXPORT_REVEAL));
        button.setId("export-reveal");
        button.getStyleClass().add("btn-secondary");
        button.setOnAction(event -> reveal.run());
        return button;
    }

    private static Node auxiliary(final Messages messages) {
        final VBox also = BriefCards.card(
                "export-also-card",
                messages,
                MessageKey.EXPORT_ALSO_TITLE,
                true,
                check("export-aux-glossary", messages, MessageKey.EXPORT_AUX_GLOSSARY),
                check("export-aux-bilingual", messages, MessageKey.EXPORT_AUX_BILINGUAL),
                check("export-aux-report", messages, MessageKey.EXPORT_AUX_REPORT));
        final ToggleSwitch consistency = new ToggleSwitch();
        consistency.setId("export-aux-consistency");
        consistency.setAccessibleText(messages.get(MessageKey.EXPORT_CONSISTENCY_TITLE));
        consistency.setDisable(true);
        final VBox pass = BriefCards.card(
                "export-consistency-card",
                messages,
                MessageKey.EXPORT_CONSISTENCY_TITLE,
                true,
                consistency,
                BriefCards.hint(messages, MessageKey.EXPORT_CONSISTENCY_NOTE));
        final VBox column = new VBox(COLUMN_SPACING, also, pass);
        column.setPrefWidth(AUX_WIDTH);
        column.setMinWidth(AUX_WIDTH);
        column.setMaxWidth(AUX_WIDTH);
        return column;
    }

    private static CheckBox check(final String id, final Messages messages, final MessageKey label) {
        final CheckBox box = new CheckBox(messages.get(label));
        box.setId(id);
        box.setDisable(true);
        return box;
    }
}
