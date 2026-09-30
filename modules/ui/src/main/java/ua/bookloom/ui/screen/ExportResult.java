package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.ui.control.StatTile;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.ExportReportLines;
import ua.bookloom.ui.state.FileRevealer;

/**
 * What the screen reports about the last export: the four result tiles, the checks that hold once the file was
 * re-opened, and Open folder and Open book.
 *
 * <p>Before an export the tiles read a dash, no check is listed and no action is offered, because each of them would
 * vouch for a file that does not exist. The language line names the source and target the person chose, and is listed
 * only for a format whose metadata the export rewrites.
 */
@Slf4j
final class ExportResult {

    private static final double TILE_SPACING = 12;
    private static final double ROW_SPACING = 8;
    private static final String DASH = "—";
    private static final String CHECK = "✓";

    private final Messages messages;
    private final Label written = number("export-written-value");
    private final Label auto = number("export-auto-value");
    private final Label reviewed = number("export-reviewed-value");
    private final Label valid = number("export-valid-value");
    private final VBox checks = new VBox(ROW_SPACING);
    private final HBox actions = new HBox(ROW_SPACING);
    private @Nullable Path file;

    ExportResult(final Messages messages, final FileRevealer revealer) {
        this.messages = Objects.requireNonNull(messages, "messages");
        checks.setId("export-checks");
        actions.setId("export-actions");
        actions.getChildren()
                .addAll(
                        action(
                                "export-reveal",
                                MessageKey.EXPORT_REVEAL,
                                MessageKey.EXPORT_REVEAL_TIP,
                                () -> revealer.reveal(target())),
                        action(
                                "export-open-book",
                                MessageKey.EXPORT_OPEN_BOOK,
                                MessageKey.EXPORT_OPEN_BOOK_TIP,
                                () -> revealer.open(target())));
        reset();
    }

    /** Whether an export of this format rewrites language metadata that the book itself declared. */
    static boolean rewritesLanguage(final BookInspection inspection) {
        final BookFormat format = Objects.requireNonNull(inspection.format(), "format");
        return switch (format) {
            case EPUB, FB2 -> true;
            case MARKDOWN -> inspection.languageEvidence().declaredRaw() != null;
            case TXT -> false;
        };
    }

    Node tiles() {
        final HBox row = new HBox(
                TILE_SPACING,
                tile("export-written", written, MessageKey.EXPORT_TILE_WRITTEN),
                tile("export-auto", auto, MessageKey.EXPORT_TILE_AUTO),
                tile("export-reviewed", reviewed, MessageKey.EXPORT_TILE_REVIEWED),
                tile("export-valid", valid, MessageKey.EXPORT_TILE_VALID));
        row.setPadding(new Insets(0));
        return row;
    }

    Node checks() {
        return checks;
    }

    Node actions() {
        return actions;
    }

    /** Fills the result for an export, or clears it when there is none; {@code source} and {@code target} are tags. */
    void show(
            final @Nullable ExportOutcome outcome,
            final BookInspection inspection,
            final String source,
            final String target) {
        if (outcome == null) {
            log.debug("the export result is cleared");
            reset();
            return;
        }
        final ExportReport report = outcome.report();
        log.debug("the export result shows {} written, {} auto-accepted", report.written(), report.autoAccepted());
        file = report.destination();
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        written.setText(grouping.format(report.written()));
        auto.setText(share(report));
        reviewed.setText(grouping.format(report.reviewed()));
        valid.setText(CHECK);
        if (!valid.getStyleClass().contains("status-ok")) {
            valid.getStyleClass().add("status-ok");
        }
        checks.getChildren().setAll(check("export-check-reopened", messages.get(MessageKey.EXPORT_CHECK_REOPENED)));
        if (rewritesLanguage(inspection)) {
            checks.getChildren()
                    .add(check(
                            "export-check-language", messages.get(MessageKey.EXPORT_CHECK_LANGUAGE, source, target)));
        }
        addReportChecks(report);
        setActionsShown(true);
    }

    private void addReportChecks(final ExportReport report) {
        final List<String> names = ExportReportLines.sideFileNames(report);
        for (int index = 0; index < names.size(); index++) {
            checks.getChildren()
                    .add(check(
                            "export-check-side-file-" + index,
                            messages.get(MessageKey.EXPORT_CHECK_SIDE_FILE, names.get(index))));
        }
        final List<String> passLines = ExportReportLines.consistency(messages, report.consistency());
        for (int index = 0; index < passLines.size(); index++) {
            checks.getChildren()
                    .add(check("export-check-consistency" + (index == 0 ? "" : "-" + index), passLines.get(index)));
        }
    }

    private void reset() {
        file = null;
        for (final Label label : new Label[] {written, auto, reviewed, valid}) {
            label.setText(DASH);
        }
        valid.getStyleClass().remove("status-ok");
        checks.getChildren().clear();
        setActionsShown(false);
    }

    private void setActionsShown(final boolean shown) {
        actions.setVisible(shown);
        actions.setManaged(shown);
    }

    private String share(final ExportReport report) {
        if (report.written() == 0) {
            return DASH;
        }
        final NumberFormat percent = NumberFormat.getPercentInstance(messages.locale());
        percent.setMaximumFractionDigits(1);
        return percent.format(report.autoAccepted() / (double) report.written());
    }

    private Path target() {
        return Objects.requireNonNull(file, "a written book, since the actions show only after an export");
    }

    private Node tile(final String id, final Label number, final MessageKey caption) {
        return new StatTile(id, number, messages.get(caption));
    }

    private static Label number(final String id) {
        final Label label = new Label();
        label.setId(id);
        return label;
    }

    private static Label check(final String id, final String text) {
        final Label line = new Label(CHECK + " " + text);
        line.setId(id);
        line.setWrapText(true);
        line.getStyleClass().add("status-ok");
        return line;
    }

    private Button action(final String id, final MessageKey caption, final MessageKey tip, final Runnable onPress) {
        final Button button = Tips.install(messages, new Button(messages.get(caption)), tip);
        button.setId(id);
        button.getStyleClass().add("btn-secondary");
        button.setOnAction(event -> {
            log.debug("{} pressed", id);
            onPress.run();
        });
        return button;
    }
}
