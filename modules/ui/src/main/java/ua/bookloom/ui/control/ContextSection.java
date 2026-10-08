package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A collapsed "Context sent to the model" section in the review panel: its header counts what the draft was given, and
 * its body shows the parts in the order the draft prompt sends them (style sheet, running summary, glossary names,
 * locked names, suggested renderings, recurring-term renderings, translation-memory hits, characters, preceding
 * translations). Copy puts the same as plain text on the clipboard.
 */
public final class ContextSection extends CopyablePane {

    private static final double SPACING = 6;
    private static final double GRID_GAP = 12;
    private static final double TERM_SHARE = 40;
    private static final String INDENT = "    ";
    private static final String CONTEXT_TEXT = "context-text";

    /**
     * Builds a hidden, collapsed section; {@link #show(ContextSnapshot)} fills it.
     *
     * @param id the section's node id; the body is {@code <id>-body} and Copy is {@code <id>-copy}
     * @param messages the catalogue the headings are worded from
     */
    public ContextSection(final String id, final Messages messages) {
        this(id, messages, CopyablePane::toClipboard);
    }

    ContextSection(final String id, final Messages messages, final Consumer<String> clipboard) {
        super(
                id,
                messages,
                clipboard,
                MessageKey.LIVE_CONTEXT_TIP,
                MessageKey.LIVE_CONTEXT_COPY,
                MessageKey.LIVE_CONTEXT_COPY_TIP);
        show(null);
    }

    /**
     * Shows what a draft was sent with, or hides the section when nothing was announced.
     *
     * @param context the context, or {@code null} to hide the section
     */
    public void show(final @Nullable ContextSnapshot context) {
        show(context, context == null ? "" : messages().get(MessageKey.LIVE_CONTEXT_TITLE) + " · " + counts(context));
    }

    /**
     * Shows what a draft was sent with under a header of the caller's wording, or hides the section.
     *
     * @param context the context, or {@code null} to hide the section
     * @param title the collapsed header; ignored when {@code context} is {@code null}
     */
    public void show(final @Nullable ContextSnapshot context, final String title) {
        Objects.requireNonNull(title, "title");
        if (context == null) {
            hideSection();
            return;
        }
        display(title, plainText(context), parts(context));
    }

    private String counts(final ContextSnapshot context) {
        return messages()
                .get(
                        MessageKey.LIVE_CONTEXT_COUNTS,
                        context.precedingTargets().size(),
                        context.summary() == null ? "no" : "yes",
                        context.glossary().size(),
                        context.tmHits().size());
    }

    private List<Node> parts(final ContextSnapshot context) {
        if (isEmpty(context)) {
            return List.of(text(CONTEXT_TEXT, messages().get(MessageKey.LIVE_CONTEXT_EMPTY)));
        }
        final List<Node> parts = new ArrayList<>();
        final String sheet = context.styleSheet();
        add(parts, MessageKey.LIVE_CONTEXT_STYLE, sheet.isBlank() ? List.of() : List.of(text(CONTEXT_TEXT, sheet)));
        final String summary = context.summary();
        add(
                parts,
                MessageKey.LIVE_CONTEXT_SUMMARY,
                List.of(
                        summary == null
                                ? text("context-none", messages().get(MessageKey.LIVE_CONTEXT_NO_SUMMARY))
                                : text(CONTEXT_TEXT, summary)));
        add(parts, MessageKey.LIVE_CONTEXT_NAMES, nameGrid(context, ContextSection::isPlain));
        add(parts, MessageKey.LIVE_CONTEXT_LOCKED_NAMES, nameGrid(context, SnapshotTerm::locked));
        add(parts, MessageKey.LIVE_CONTEXT_SUGGESTED, nameGrid(context, ContextSection::isSuggested));
        addRecalled(parts, context);
        return parts;
    }

    // Renderings the run kept, memory hits, characters and the preceding translations: the parts made of plain lines.
    private void addRecalled(final List<Node> parts, final ContextSnapshot context) {
        add(
                parts,
                MessageKey.LIVE_CONTEXT_LEXICON,
                lines(context.lexicon().stream().map(this::renderingLine)));
        add(
                parts,
                MessageKey.LIVE_CONTEXT_MEMORY,
                lines(context.tmHits().stream().map(this::hitLine)));
        add(parts, MessageKey.LIVE_CONTEXT_CHARACTERS, lines(context.characters().stream()));
        add(
                parts,
                MessageKey.LIVE_CONTEXT_PRECEDING,
                context.precedingTargets().stream()
                        .<Node>map(target -> text("context-quote", target))
                        .toList());
    }

    private static List<Node> lines(final Stream<String> lines) {
        return lines.<Node>map(line -> text(CONTEXT_TEXT, line)).toList();
    }

    // A draft sent nothing at all gets the one line that says so; the style sheet alone does not count as context.
    private static boolean isEmpty(final ContextSnapshot context) {
        return context.precedingTargets().isEmpty()
                && context.summary() == null
                && context.glossary().isEmpty()
                && context.tmHits().isEmpty()
                && context.lexicon().isEmpty()
                && context.characters().isEmpty();
    }

    private static boolean isPlain(final SnapshotTerm term) {
        return !term.locked() && !term.suggested();
    }

    private static boolean isSuggested(final SnapshotTerm term) {
        return !term.locked() && term.suggested();
    }

    private void add(final List<Node> parts, final MessageKey heading, final List<? extends Node> nodes) {
        if (!nodes.isEmpty()) {
            parts.add(block(messages().get(heading), nodes));
        }
    }

    private List<Node> nameGrid(final ContextSnapshot context, final Predicate<SnapshotTerm> which) {
        final List<SnapshotTerm> terms =
                context.glossary().stream().filter(which).toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        final GridPane grid = new GridPane(GRID_GAP, SPACING);
        grid.getStyleClass().add("context-names");
        final ColumnConstraints term = new ColumnConstraints();
        term.setPercentWidth(TERM_SHARE);
        final ColumnConstraints target = new ColumnConstraints();
        target.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(term, target);
        for (int row = 0; row < terms.size(); row++) {
            grid.addRow(row, text("context-term", terms.get(row).term()), rendering(terms.get(row)));
        }
        return List.of(grid);
    }

    private Node rendering(final SnapshotTerm term) {
        final String target = term.target();
        final Label label = target == null || target.isBlank()
                ? text("context-none", messages().get(MessageKey.LIVE_CONTEXT_NO_RENDERING))
                : text(CONTEXT_TEXT, target);
        if (!term.locked()) {
            return label;
        }
        final FontIcon lock = new FontIcon(Feather.LOCK);
        lock.getStyleClass().add("context-lock");
        final String locked = messages().get(MessageKey.LIVE_CONTEXT_LOCKED);
        lock.setAccessibleText(locked);
        Tooltip.install(lock, new Tooltip(locked));
        final HBox cell = new HBox(SPACING, label, lock);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private String plainText(final ContextSnapshot context) {
        final StringJoiner text = new StringJoiner("\n\n");
        final String sheet = context.styleSheet();
        section(text, MessageKey.LIVE_CONTEXT_STYLE, sheet.isBlank() ? List.of() : List.of(sheet));
        final String summary = context.summary();
        section(text, MessageKey.LIVE_CONTEXT_SUMMARY, summary == null ? List.of() : List.of(summary));
        section(text, MessageKey.LIVE_CONTEXT_NAMES, termLines(context, ContextSection::isPlain));
        section(text, MessageKey.LIVE_CONTEXT_LOCKED_NAMES, termLines(context, SnapshotTerm::locked));
        section(text, MessageKey.LIVE_CONTEXT_SUGGESTED, termLines(context, ContextSection::isSuggested));
        section(
                text,
                MessageKey.LIVE_CONTEXT_LEXICON,
                context.lexicon().stream().map(this::renderingLine).toList());
        section(
                text,
                MessageKey.LIVE_CONTEXT_MEMORY,
                context.tmHits().stream().map(this::hitLine).toList());
        section(text, MessageKey.LIVE_CONTEXT_CHARACTERS, context.characters());
        section(
                text,
                MessageKey.LIVE_CONTEXT_PRECEDING,
                context.precedingTargets().stream()
                        .map(target -> "“" + target + "”")
                        .toList());
        return text.length() == 0 || isEmpty(context) ? messages().get(MessageKey.LIVE_CONTEXT_EMPTY) : text.toString();
    }

    private void section(final StringJoiner text, final MessageKey heading, final List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        final StringJoiner block = new StringJoiner("\n" + INDENT, messages().get(heading) + "\n" + INDENT, "");
        lines.forEach(block::add);
        text.add(block.toString());
    }

    private List<String> termLines(final ContextSnapshot context, final Predicate<SnapshotTerm> which) {
        return context.glossary().stream().filter(which).map(this::termLine).toList();
    }

    private String termLine(final SnapshotTerm term) {
        final String target = term.target();
        return messages()
                .get(
                        MessageKey.LIVE_CONTEXT_TERM,
                        term.term(),
                        target == null || target.isBlank() ? "none" : target,
                        term.locked() ? "locked" : "free");
    }

    private String hitLine(final SnapshotTmHit hit) {
        return messages().get(MessageKey.LIVE_CONTEXT_HIT, hit.source(), hit.target());
    }

    private String renderingLine(final SnapshotRendering rendering) {
        return messages().get(MessageKey.LIVE_CONTEXT_HIT, rendering.term(), rendering.rendering());
    }
}
