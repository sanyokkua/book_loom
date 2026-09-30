package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StructureStatistics;

/**
 * The card beside the tree that states what will be translated and what is carried through untouched: the book's
 * statistics in eight rows, each value addressable as {@code structure-stat-<row>}.
 */
final class StructureStatsCard {

    private static final double GAP = 8;
    private static final double LABEL_GAP = 24;

    private final Messages messages;
    private final NumberFormat numbers;
    private final GridPane grid = new GridPane(LABEL_GAP, GAP);
    private int row;

    StructureStatsCard(final Messages messages, final NumberFormat numbers) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.numbers = Objects.requireNonNull(numbers, "numbers");
    }

    Node build(final BookStats stats) {
        Objects.requireNonNull(stats, "stats");
        final StructureStatistics figures = new StructureStatistics(stats);
        row("segments", MessageKey.STRUCTURE_STAT_SEGMENTS, numbers.format(stats.segments()), "stats-value-strong");
        row(
                "words",
                MessageKey.STRUCTURE_STAT_WORDS,
                messages.get(MessageKey.STRUCTURE_STAT_WORDS_VALUE, figures.approximateWords()),
                "stats-value");
        row("images", MessageKey.STRUCTURE_STAT_IMAGES, numbers.format(stats.images()), "stats-value");
        row("code", MessageKey.STRUCTURE_STAT_CODE, numbers.format(stats.codeBlocks()), "stats-value");
        codeNote();
        row("fonts", MessageKey.STRUCTURE_STAT_FONTS, numbers.format(stats.fonts()), "stats-value");
        row("verse", MessageKey.STRUCTURE_STAT_VERSE, verse(stats.verseLines()), "stats-value");
        row(
                "notes",
                MessageKey.STRUCTURE_STAT_NOTES,
                numbers.format(stats.footnotes()) + " · " + numbers.format(stats.tables()),
                "stats-value");
        row(
                "formatting",
                MessageKey.STRUCTURE_STAT_FORMATTING,
                formatting(figures.protectedFormatting()),
                "stats-value");
        final VBox card = new VBox(grid);
        card.setId("structure-stats-card");
        card.getStyleClass().add("card");
        return card;
    }

    private void row(final String name, final MessageKey caption, final String value, final String valueClass) {
        final Label label = new Label(messages.get(caption));
        label.getStyleClass().add("muted");
        final Label shown = new Label(value);
        shown.setId("structure-stat-" + name);
        shown.setWrapText(true);
        shown.getStyleClass().add(valueClass);
        grid.add(label, 0, row);
        grid.add(shown, 1, row);
        row++;
    }

    private void codeNote() {
        final Label note = new Label(messages.get(MessageKey.STRUCTURE_STAT_CODE_NOTE));
        note.setId("structure-stat-codeNote");
        note.setWrapText(true);
        note.getStyleClass().addAll("chip", "chip-neutral");
        grid.add(note, 1, row++);
    }

    private String verse(final int lines) {
        return lines == 0 ? messages.get(MessageKey.STRUCTURE_STAT_NONE) : numbers.format(lines);
    }

    private String formatting(final List<Formatting> kinds) {
        if (kinds.isEmpty()) {
            return messages.get(MessageKey.STRUCTURE_STAT_NONE);
        }
        final String names = kinds.stream().map(this::nameOf).collect(Collectors.joining(", "));
        return messages.get(MessageKey.STRUCTURE_STAT_FORMATTING_VALUE, names);
    }

    private String nameOf(final Formatting kind) {
        return messages.get(
                switch (kind) {
                    case ITALICS -> MessageKey.STRUCTURE_FORMAT_ITALICS;
                    case BOLD -> MessageKey.STRUCTURE_FORMAT_BOLD;
                    case LINKS -> MessageKey.STRUCTURE_FORMAT_LINKS;
                    case QUOTES -> MessageKey.STRUCTURE_FORMAT_QUOTES;
                    case CODE -> MessageKey.STRUCTURE_FORMAT_CODE;
                    case LINE_BREAKS -> MessageKey.STRUCTURE_FORMAT_LINE_BREAKS;
                    case OTHER -> MessageKey.STRUCTURE_FORMAT_OTHER;
                });
    }
}
