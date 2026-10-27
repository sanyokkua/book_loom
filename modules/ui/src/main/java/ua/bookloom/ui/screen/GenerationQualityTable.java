package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.ui.control.ReadOnlyGrid;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.QualityLabels;

/**
 * What each quality setting does, as a grid of labels only: the rows come from the pipeline's own table through
 * {@link DialProfiles}, so the window never keeps a second copy of the numbers.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GenerationQualityTable {

    private static final double[] WIDTHS = {5, 2, 2, 2};
    private static final double GAP = 6;
    private static final List<QualityDial> DIALS = List.of(QualityDial.FAST, QualityDial.BALANCED, QualityDial.MAX);

    private record Row(String name, MessageKey label, Function<DialProfile, String> cell) {}

    static GridPane of(final Messages messages, final DialProfiles profiles) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(profiles, "profiles");
        final GridPane grid = ReadOnlyGrid.of("generation-quality", WIDTHS);
        grid.setHgap(GAP);
        grid.setVgap(GAP);
        for (final QualityDial dial : DIALS) {
            grid.add(
                    cell(
                            "generation-quality-head-" + dial.name().toLowerCase(Locale.ROOT),
                            messages.get(QualityLabels.of(dial)),
                            "muted"),
                    column(dial),
                    0);
        }
        final List<Row> rows = rows(messages);
        for (int index = 0; index < rows.size(); index++) {
            addRow(grid, messages, profiles, rows.get(index), index + 1);
        }
        return grid;
    }

    private static void addRow(
            final GridPane grid, final Messages messages, final DialProfiles profiles, final Row row, final int line) {
        grid.add(cell("generation-quality-label-" + row.name(), messages.get(row.label()), "kv-key"), 0, line);
        for (final QualityDial dial : DIALS) {
            final String id =
                    "generation-quality-" + row.name() + "-" + dial.name().toLowerCase(Locale.ROOT);
            grid.add(cell(id, row.cell().apply(profiles.of(dial)), "kv-value"), column(dial), line);
        }
    }

    private static List<Row> rows(final Messages messages) {
        return List.of(
                new Row(
                        "batch",
                        MessageKey.SETTINGS_GENERATION_QUALITY_BATCH,
                        profile -> messages.get(MessageKey.SETTINGS_GENERATION_QUALITY_UP_TO, profile.chunkCap())),
                new Row(
                        "preceding",
                        MessageKey.SETTINGS_GENERATION_QUALITY_PRECEDING,
                        profile -> Integer.toString(profile.precedingTargets())),
                new Row(
                        "repair",
                        MessageKey.SETTINGS_GENERATION_QUALITY_REPAIR,
                        profile -> Integer.toString(profile.repairRounds())),
                new Row(
                        "reviews",
                        MessageKey.SETTINGS_GENERATION_QUALITY_REVIEWS,
                        profile -> Integer.toString(profile.reviewPasses())),
                new Row(
                        "backward",
                        MessageKey.SETTINGS_GENERATION_QUALITY_BACKWARD,
                        profile -> mark(messages, profile.backwardRevision())),
                new Row(
                        "summary",
                        MessageKey.SETTINGS_GENERATION_QUALITY_SUMMARY,
                        profile -> mark(messages, profile.llmSummary())));
    }

    private static String mark(final Messages messages, final boolean runs) {
        return messages.get(
                runs ? MessageKey.SETTINGS_GENERATION_QUALITY_YES : MessageKey.SETTINGS_GENERATION_QUALITY_NO);
    }

    private static int column(final QualityDial dial) {
        return DIALS.indexOf(dial) + 1;
    }

    private static Label cell(final String id, final String text, final String style) {
        final Label label = new Label(text);
        label.setId(id);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }
}
