package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.control.StatTile;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunFigures;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;

/**
 * The parts of the Translating screen that show numbers: the progress card with its line and pace, the four running
 * tiles, and the outcome card that reports what a finished or failed run decided.
 *
 * <p>Every label is bound to the mirror, and none logs: they change with every batch of decisions and every pace
 * tick, which is the case the logging rule forbids logging in a listener for. Each part of the progress line and of
 * the pace text is left out while it is unknown, so an unstarted run shows no "chapter 0 of 0".
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingFigures {

    private static final double CARD_SPACING = 8;
    private static final double TILE_SPACING = 12;
    private static final int PERCENT = 100;
    private static final String PART_SEPARATOR = " · ";

    static Node progressCard(final StateMirror mirror, final Messages messages) {
        final ProgressBar bar = new ProgressBar();
        bar.setId("translating-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(mirror.progressFraction());
        final Label line = boundLabel(
                "translating-progress-text",
                "muted",
                () -> progressLine(mirror, messages),
                mirror.figures(),
                mirror.section(),
                mirror.sections(),
                mirror.chunk(),
                mirror.chunks());
        final Label pace = boundLabel(
                "translating-pace-text",
                "muted",
                () -> paceText(mirror.live().throughput().get(), messages),
                mirror.live().throughput());
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final VBox card = new VBox(CARD_SPACING, bar, new HBox(TILE_SPACING, line, spacer, pace));
        card.setId("translating-progress-card");
        card.getStyleClass().add("card");
        return card;
    }

    /** One count tile: the ids of its tile and its number, which figure it shows, its caption and its role. */
    private record Tile(
            String tileId, String labelId, ToIntFunction<RunFigures> count, MessageKey caption, boolean warning) {}

    private static final List<Tile> RUNNING = List.of(
            new Tile(
                    "translating-tile-accepted",
                    "translating-count-accepted",
                    RunFigures::autoAccepted,
                    MessageKey.TRANSLATING_COUNT_AUTO,
                    false),
            new Tile(
                    "translating-tile-repaired",
                    "translating-count-repaired",
                    RunFigures::repaired,
                    MessageKey.TRANSLATING_COUNT_REPAIRED,
                    false),
            new Tile(
                    "translating-tile-flagged",
                    "translating-count-flagged",
                    RunFigures::flagged,
                    MessageKey.TRANSLATING_COUNT_FLAGGED,
                    true),
            new Tile(
                    "translating-tile-remaining",
                    "translating-count-remaining",
                    RunFigures::remaining,
                    MessageKey.TRANSLATING_COUNT_REMAINING,
                    false));

    private static final List<Tile> OUTCOME = List.of(
            new Tile(
                    "translating-outcome-tile-accepted",
                    "translating-outcome-accepted",
                    RunFigures::autoAccepted,
                    MessageKey.TRANSLATING_COUNT_AUTO,
                    false),
            new Tile(
                    "translating-outcome-tile-repaired",
                    "translating-outcome-repaired",
                    RunFigures::repaired,
                    MessageKey.TRANSLATING_COUNT_REPAIRED,
                    false),
            new Tile(
                    "translating-outcome-tile-flagged",
                    "translating-outcome-flagged",
                    RunFigures::flagged,
                    MessageKey.TRANSLATING_COUNT_FLAGGED,
                    true));

    static Node runningTiles(final StateMirror mirror, final Messages messages) {
        final HBox row = new HBox(TILE_SPACING);
        RUNNING.forEach(spec -> row.getChildren().add(tile(spec, mirror, messages)));
        row.setId("translating-tiles");
        return row;
    }

    static Node outcomeCard(final StateMirror mirror, final Messages messages) {
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final Label kept = boundLabel(
                "translating-outcome-kept",
                "stat-number",
                () -> grouping.format(mirror.live().sourceKept().get()),
                mirror.live().sourceKept());
        final HBox row = new HBox(TILE_SPACING);
        OUTCOME.forEach(spec -> row.getChildren().add(tile(spec, mirror, messages)));
        row.getChildren()
                .add(new StatTile(
                        "translating-outcome-tile-kept", kept, messages.get(MessageKey.TRANSLATING_COUNT_KEPT)));
        return BriefCards.card("translating-outcome-card", messages, MessageKey.TRANSLATING_OUTCOME_TITLE, row);
    }

    private static Node tile(final Tile spec, final StateMirror mirror, final Messages messages) {
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final Label number = boundLabel(
                spec.labelId(),
                spec.warning() ? "stat-number-warn" : "stat-number",
                () -> grouping.format(spec.count().applyAsInt(mirror.figures().get())),
                mirror.figures());
        return new StatTile(spec.tileId(), number, messages.get(spec.caption()));
    }

    static String progressLine(final StateMirror mirror, final Messages messages) {
        final RunFigures figures = mirror.figures().get();
        final int decided = figures.autoAccepted() + figures.repaired() + figures.flagged();
        final int percent = figures.total() == 0 ? 0 : (int) ((long) decided * PERCENT / figures.total());
        final List<String> parts = new ArrayList<>();
        parts.add(messages.get(MessageKey.TRANSLATING_LINE_PERCENT, percent));
        if (mirror.sections().get() > 0) {
            parts.add(messages.get(
                    MessageKey.TRANSLATING_LINE_SECTION,
                    mirror.section().get(),
                    mirror.sections().get()));
        }
        if (mirror.chunks().get() > 0) {
            parts.add(messages.get(
                    MessageKey.TRANSLATING_LINE_CHUNK,
                    mirror.chunk().get(),
                    mirror.chunks().get()));
        }
        return String.join(PART_SEPARATOR, parts);
    }

    static String paceText(final Throughput pace, final Messages messages) {
        Objects.requireNonNull(pace, "pace");
        final List<String> parts = new ArrayList<>();
        if (pace.timeLeft() != null) {
            parts.add(messages.get(MessageKey.TRANSLATING_PACE_LEFT, DurationText.format(messages, pace.timeLeft())));
        }
        if (pace.tokensPerSecond() != null) {
            final String rate = (pace.estimated() ? "~" : "") + Math.round(pace.tokensPerSecond());
            parts.add(messages.get(MessageKey.TRANSLATING_PACE_RATE, rate));
        }
        return String.join(PART_SEPARATOR, parts);
    }

    static Label boundLabel(
            final String id, final String styleClass, final Callable<String> text, final Observable... sources) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add(styleClass);
        label.textProperty().bind(Bindings.createStringBinding(text, sources));
        return label;
    }
}
