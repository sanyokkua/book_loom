package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
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
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.StatTile;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportFigures;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.ExportReportLines;
import ua.bookloom.ui.state.FileRevealer;

/**
 * What the screen reports about the book and its last export: the four tiles, the checks that hold once the file was
 * re-opened, and the visibility of Open folder and Open book, which the toolbar holds.
 *
 * <p>The tiles come from the project: before any export the first reads the segments done of those that could be,
 * after one it reads what the stored export wrote, also after a restart, and the fourth says the written file was
 * checked; the second and third are always the live counts. No check is listed and no action is offered until an
 * export is shown, because each would vouch for a file that does not exist. The language line names the source and
 * target the person chose, and is listed only for a format whose metadata the export rewrites.
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
    private final Label writtenCaption = new Label();
    private final Label validCaption = new Label();
    private final Navigator navigator;
    private final VBox checks = new VBox(ROW_SPACING);
    private final List<Button> actions;
    private @Nullable Path file;

    ExportResult(final Messages messages, final FileRevealer revealer, final Navigator navigator) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        checks.setId("export-checks");
        Visibility.bind(checks, Bindings.isNotEmpty(checks.getChildren()));
        actions = List.of(
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
        showFigures(null);
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
                Tips.install(
                        messages,
                        new StatTile("export-written", written, writtenCaption),
                        MessageKey.TRANSLATING_STRIP_DONE_TIP),
                Tips.install(
                        messages,
                        tile("export-auto", auto, MessageKey.EXPORT_TILE_AUTO),
                        MessageKey.TRANSLATING_STRIP_DONE_TIP),
                Tips.install(
                        messages,
                        tile("export-reviewed", reviewed, MessageKey.EXPORT_TILE_REVIEWED),
                        MessageKey.TRANSLATING_STRIP_DONE_TIP),
                new StatTile("export-valid", valid, validCaption));
        row.setPadding(new Insets(0));
        return row;
    }

    Node checks() {
        return checks;
    }

    /** Open folder and Open book, which the toolbar holds from the moment a file is written. */
    List<Button> toolbarActions() {
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
        log.debug("the export result shows {} written", report.written());
        file = report.destination();
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
        ExportReportLines.sourceFallbacks(messages, report)
                .ifPresent(line -> checks.getChildren().add(warning("export-check-source-fallbacks", line)));
        ExportReportLines.noTargetFallbacks(messages, report)
                .ifPresent(line -> checks.getChildren().add(warning("export-check-no-target", line)));
        final Optional<String> unknown = ExportReportLines.awaitingGender(messages, report.consistency());
        final Optional<String> suggested = ExportReportLines.suggestedGender(messages, report.consistency());
        unknown.ifPresent(line -> checks.getChildren().add(warning("export-check-awaiting-gender", line)));
        suggested.ifPresent(line -> checks.getChildren().add(warning("export-check-suggested-gender", line)));
        if (unknown.isPresent() || suggested.isPresent()) {
            checks.getChildren().add(namesStyleLink());
        }
        final List<String> passLines = ExportReportLines.consistency(messages, report.consistency());
        for (int index = 0; index < passLines.size(); index++) {
            checks.getChildren()
                    .add(check("export-check-consistency" + (index == 0 ? "" : "-" + index), passLines.get(index)));
        }
    }

    private Button namesStyleLink() {
        return action(
                "export-awaiting-gender-open",
                MessageKey.EXPORT_AWAITING_GENDER_OPEN,
                MessageKey.EXPORT_AWAITING_GENDER_OPEN_TIP,
                () -> navigator.navigate(ViewNames.NAMES_STYLE));
    }

    /**
     * Fills the tiles from the project's figures; a dash while they are not known.
     *
     * @param figures the counts and the last written export, or null
     */
    void showFigures(final @Nullable ExportFigures figures) {
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final ExportRecord last = figures == null ? null : figures.last();
        log.debug("the tiles show figures known {}, from the stored export {}", figures != null, last != null);
        writtenCaption.setText(
                messages.get(last == null ? MessageKey.EXPORT_TILE_DONE : MessageKey.EXPORT_TILE_WRITTEN));
        validCaption.setText(
                messages.get(last == null ? MessageKey.EXPORT_TILE_VALID : MessageKey.EXPORT_TILE_VALID_DONE));
        valid.setText(last == null ? DASH : CHECK);
        valid.getStyleClass().remove("status-ok");
        if (last != null) {
            valid.getStyleClass().add("status-ok");
        }
        if (figures == null) {
            for (final Label label : new Label[] {written, auto, reviewed}) {
                label.setText(DASH);
            }
            return;
        }
        written.setText(
                last == null
                        ? messages.get(MessageKey.EXPORT_TILE_OF, figures.translated(), figures.ofTotal())
                        : messages.get(MessageKey.EXPORT_TILE_OF, last.written(), last.written() + last.pending()));
        auto.setText(grouping.format(figures.counts().autoAccepted()));
        reviewed.setText(grouping.format(figures.counts().reviewed()));
    }

    private void reset() {
        file = null;
        checks.getChildren().clear();
        setActionsShown(false);
    }

    private void setActionsShown(final boolean shown) {
        for (final Button button : actions) {
            button.setVisible(shown);
            button.setManaged(shown);
        }
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

    private static Label warning(final String id, final String text) {
        final Label line = new Label(text);
        line.setId(id);
        line.setWrapText(true);
        line.getStyleClass().add("status-warn");
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
