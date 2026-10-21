package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Turns one filled prompt part into the shape its slot has: bullets for the style sheet, a Term | Rendering | Note
 * grid for the glossary and the recurring terms, an Id | Segment | Text grid for the items, a Source | Translation grid
 * for earlier pairs, and wrapped text for any other slot or for a part whose lines do not fit its shape. Rendering
 * logs nothing: a live call is redrawn whenever it changes, and the text of a part is book text.
 */
final class PromptParts {

    private static final Pattern TERM = Pattern.compile("^(.+?) → (.*?)(?: \\((.*)\\))?$");
    private static final Pattern ITEM = Pattern.compile("<s id=\"([^\"]*)\">(.*?)</s>", Pattern.DOTALL);
    private static final Pattern PAIR =
            Pattern.compile("Source: (.*?)\nTranslation: (.*?)(?=\n\nSource: |\\z)", Pattern.DOTALL);
    private static final String NONE = "—";
    private static final double SPACING = 4;
    private static final double[] TERM_WIDTHS = {30, 40, 30};
    private static final double[] ITEM_WIDTHS = {10, 22, 68};
    private static final double[] PAIR_WIDTHS = {50, 50};

    private final Messages messages;
    private final Map<String, String> locators;

    PromptParts(final Messages messages, final Map<String, String> locators) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.locators = Objects.requireNonNull(locators, "locators");
    }

    /**
     * The node that shows a part.
     *
     * @param section the part
     * @param id the node id of the returned node
     * @return the part in the shape of its slot, never null
     */
    Node render(final PromptSection section, final String id) {
        final List<String> lines = section.lines();
        final Optional<Node> parsed =
                switch (section.slot()) {
                    case "styleSheet" -> Optional.of(bullets(lines, id));
                    case "glossaryTerms", "suggestedTerms", "lexiconTerms" -> Optional.of(terms(lines, id));
                    case "items" -> items(lines, id);
                    case "precedingPairs", "relevantPairs" -> pairs(lines, id);
                    default -> Optional.empty();
                };
        return parsed.orElseGet(() -> plain(lines, id));
    }

    private static Node bullets(final List<String> lines, final String id) {
        final VBox box = new VBox(SPACING);
        for (final String line : lines) {
            if (!line.isBlank()) {
                box.getChildren().add(label("• " + line.strip(), "prompt-bullet"));
            }
        }
        box.setId(id);
        return box;
    }

    private Node terms(final List<String> lines, final String id) {
        final List<List<String>> rows = new ArrayList<>();
        for (final String line : lines) {
            final Matcher match = TERM.matcher(line.strip());
            rows.add(
                    match.matches()
                            ? List.of(match.group(1), match.group(2), match.group(3) == null ? "" : match.group(3))
                            : List.of(line.strip(), "", ""));
        }
        return grid(
                id,
                List.of(
                        messages.get(MessageKey.LIVE_CALL_COL_TERM),
                        messages.get(MessageKey.LIVE_CALL_COL_RENDERING),
                        messages.get(MessageKey.LIVE_CALL_COL_NOTE)),
                TERM_WIDTHS,
                rows);
    }

    private Optional<Node> items(final List<String> lines, final String id) {
        final Matcher match = ITEM.matcher(String.join("\n", lines));
        final List<List<String>> rows = new ArrayList<>();
        while (match.find()) {
            rows.add(List.of(match.group(1), locators.getOrDefault(match.group(1), NONE), match.group(2)));
        }
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(grid(
                id,
                List.of(
                        messages.get(MessageKey.LIVE_CALL_COL_ID),
                        messages.get(MessageKey.LIVE_CALL_COL_SEGMENT),
                        messages.get(MessageKey.LIVE_CALL_COL_TEXT)),
                ITEM_WIDTHS,
                rows));
    }

    private Optional<Node> pairs(final List<String> lines, final String id) {
        final Matcher match = PAIR.matcher(String.join("\n", lines));
        final List<List<String>> rows = new ArrayList<>();
        while (match.find()) {
            rows.add(List.of(match.group(1), match.group(2)));
        }
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(grid(
                id,
                List.of(
                        messages.get(MessageKey.LIVE_CALL_COL_SOURCE),
                        messages.get(MessageKey.LIVE_CALL_COL_TRANSLATION)),
                PAIR_WIDTHS,
                rows));
    }

    private static Node plain(final List<String> lines, final String id) {
        final Label text = label(String.join("\n", lines), "prompt-plain");
        text.setId(id);
        return text;
    }

    private static Node grid(
            final String id, final List<String> heads, final double[] widths, final List<List<String>> rows) {
        final ParsedGrid grid = new ParsedGrid(id, heads, widths);
        for (final List<String> row : rows) {
            grid.addRow(row, null);
        }
        return grid;
    }

    private static Label label(final String text, final String role) {
        final Label label = new Label(text);
        label.getStyleClass().addAll("context-text", role);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
