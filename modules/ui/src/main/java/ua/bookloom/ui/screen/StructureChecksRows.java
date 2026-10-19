package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StatusRole;
import ua.bookloom.ui.state.StructureChecks;
import ua.bookloom.ui.state.StructureChecksViewModel;

/**
 * The rows that report the background checks: the round trip, the identifiers and, when a segment is larger than one
 * chunk, the warning that it will be split. A card of its own holds them on the structure screen; the facts card holds
 * them among the counts on the import screen.
 *
 * <p>The rows redraw whenever the view model publishes, and hold the listener themselves, so that the weak
 * registration on the long-lived view model lets both go when the screen does. They never touch Continue: a check
 * informs and does not block.
 */
@Slf4j
final class StructureChecksRows extends VBox {

    private static final double ROW_SPACING = 12;
    private static final double GLYPH_SPACING = 10;
    private static final String WARNING_GLYPH = "⚠";

    private final StructureChecksViewModel checks;
    private final Messages messages;
    private final NumberFormat numbers;
    private final ChangeListener<StructureChecks> onChecks = (observed, was, now) -> render(now);

    StructureChecksRows(final StructureChecksViewModel checks, final Messages messages, final NumberFormat numbers) {
        super(ROW_SPACING);
        this.checks = Objects.requireNonNull(checks, "checks");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.numbers = Objects.requireNonNull(numbers, "numbers");
        checks.state().addListener(new WeakChangeListener<>(onChecks));
        render(checks.state().get());
    }

    // Translating starts its remaining count from every segment a run translates, which is more than the tree's book
    // text whenever the brief also translates titles, image descriptions, contents or book details; this line says so.
    Label runTotalLine(final int bookText) {
        log.debug("building the run-total line over {} segment(s) of book text", bookText);
        final Label line = new Label();
        line.setId("structure-run-total");
        line.getStyleClass().add("muted");
        line.setWrapText(true);
        line.setMinHeight(Region.USE_PREF_SIZE);
        line.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> checks.state().get() instanceof StructureChecks.Finished finished
                                        && finished.runSegments() > bookText
                                ? messages.get(
                                        MessageKey.STRUCTURE_RUN_TOTAL,
                                        bookText,
                                        finished.runSegments() - bookText,
                                        finished.runSegments())
                                : "",
                        checks.state()));
        line.visibleProperty().bind(line.textProperty().isNotEmpty());
        line.managedProperty().bind(line.visibleProperty());
        return line;
    }

    private void render(final StructureChecks state) {
        log.debug("rendering the structure checks: {}", state.getClass().getSimpleName());
        final List<Node> rows = new ArrayList<>();
        switch (state) {
            case StructureChecks.Running _ ->
                rows.add(row("structure-check-roundtrip", "…", StatusRole.INFO, MessageKey.STRUCTURE_CHECK_RUNNING));
            case StructureChecks.Finished finished -> addFinished(rows, finished);
        }
        getChildren().setAll(rows);
    }

    private void addFinished(final List<Node> rows, final StructureChecks.Finished finished) {
        if (finished.hasReport()) {
            final RoundTripReport report = finished.requiredReport();
            rows.add(roundTripRow(report));
            rows.add(idsRow(report));
        } else {
            rows.add(row("structure-check-roundtrip", "!", StatusRole.WARNING, MessageKey.STRUCTURE_CHECK_UNAVAILABLE));
        }
        if (finished.oversizedSegments() > 0) {
            rows.add(warning(finished.oversizedSegments()));
        }
    }

    private Node roundTripRow(final RoundTripReport report) {
        if (report.structurePreserved()) {
            return row("structure-check-roundtrip", "✓", StatusRole.SUCCESS, MessageKey.STRUCTURE_CHECK_PASSED);
        }
        if (report.sourceSegments() != report.copySegments()) {
            return row(
                    "structure-check-roundtrip",
                    "✗",
                    StatusRole.DANGER,
                    MessageKey.STRUCTURE_CHECK_FAILED_COUNTS,
                    numbers.format(report.copySegments()),
                    report.sourceSegments());
        }
        return row("structure-check-roundtrip", "✗", StatusRole.DANGER, MessageKey.STRUCTURE_CHECK_FAILED);
    }

    private Node idsRow(final RoundTripReport report) {
        if (report.idsPreserved() && report.missingIds().isEmpty()) {
            return row("structure-check-ids", "✓", StatusRole.SUCCESS, MessageKey.STRUCTURE_CHECK_IDS_OK);
        }
        return row(
                "structure-check-ids",
                "✗",
                StatusRole.DANGER,
                MessageKey.STRUCTURE_CHECK_IDS_MISSING,
                String.join(", ", report.missingIds()));
    }

    private Node warning(final int oversized) {
        return new Banner(
                "structure-chunk-warning",
                Banner.Role.WARN,
                WARNING_GLYPH,
                "",
                messages.get(MessageKey.STRUCTURE_CHECK_OVERSIZED, oversized));
    }

    private Node row(
            final String id, final String glyph, final StatusRole role, final MessageKey text, final Object... args) {
        final Label mark = new Label(glyph);
        mark.getStyleClass().add(role.styleClass());
        final Label words = new Label(messages.get(text, args));
        words.setId(id);
        words.getStyleClass().add("stats-value");
        words.setWrapText(true);
        final HBox row = new HBox(GLYPH_SPACING, mark, words);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }
}
