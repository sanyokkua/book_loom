package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SectionMemory;

/**
 * A collapsed "Context sent to the model" section, under each live row and in the review panel: its header counts what
 * the draft was given (the review panel names the parts instead), and its
 * body shows the preceding translations as quote blocks, the running summary, the glossary names as a two-column grid
 * with a lock on the locked ones, and the translation-memory hits; Copy puts the same as plain text on the clipboard.
 *
 * <p>Opened, the body takes the height of what it shows at the row's width, up to {@value #BODY_MAX} pixels, and scrolls
 * inside past that, so the row, the live card and the page grow with it and nothing crushes it to a line. It stays collapsed or open as the person left it while the rows
 * it shows change. Showing logs nothing, as it is redrawn on every live-row change; only a press of Copy is logged.
 */
@Slf4j
public final class ContextSection extends TitledPane {

    static final double BODY_MAX = 320;

    private static final double SPACING = 6;
    private static final double SECTION_SPACING = 10;
    private static final double GRID_GAP = 12;
    private static final double TERM_SHARE = 40;
    private static final String INDENT = "    ";

    private final Messages messages;
    private final Consumer<String> clipboard;
    private final VBox sections = new VBox(SECTION_SPACING);
    private String plain = "";

    /**
     * Builds a hidden, collapsed section; {@link #show(ContextSnapshot)} fills it.
     *
     * @param id the section's node id; the body is {@code <id>-body} and Copy is {@code <id>-copy}
     * @param messages the catalogue the headings are worded from
     */
    public ContextSection(final String id, final Messages messages) {
        this(id, messages, ContextSection::toClipboard);
    }

    ContextSection(final String id, final Messages messages, final Consumer<String> clipboard) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        setId(id);
        getStyleClass().add("context-section");
        setExpanded(false);
        setAnimated(false);
        sections.getStyleClass().add("context-sections");
        final ContextBody body = new ContextBody(sections);
        body.setId(id + "-body");
        body.getStyleClass().add("context-body");
        final VBox content = new VBox(SPACING, copyRow(id), body);
        setContent(content);
        Tips.install(messages, this, MessageKey.LIVE_CONTEXT_TIP);
        show(null);
    }

    /**
     * Keeps this section open or closed as the person last left a section with the same id this session, and records
     * each later change there, so a screen rebuilt on the next visit does not close it.
     *
     * @param memory the session's memory of open sections
     */
    public void rememberIn(final SectionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        setExpanded(memory.isOpen(getId()));
        expandedProperty().addListener((observed, was, now) -> memory.remember(getId(), now));
    }

    private HBox copyRow(final String id) {
        final Button copy = Tips.install(
                messages, new Button(messages.get(MessageKey.LIVE_CONTEXT_COPY)), MessageKey.LIVE_CONTEXT_COPY_TIP);
        copy.setId(id + "-copy");
        copy.getStyleClass().add("btn-ghost");
        copy.setOnAction(event -> {
            log.debug("copying the context of {} ({} characters)", getId(), plain.length());
            clipboard.accept(plain);
        });
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox row = new HBox(spacer, copy);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private static void toClipboard(final String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Shows what a draft was sent with, or hides the section when nothing was announced.
     *
     * @param context the context, or {@code null} to hide the section
     */
    public void show(final @Nullable ContextSnapshot context) {
        show(context, context == null ? "" : messages.get(MessageKey.LIVE_CONTEXT_TITLE) + " · " + counts(context));
    }

    /**
     * Shows what a draft was sent with under a header of the caller's wording, or hides the section.
     *
     * @param context the context, or {@code null} to hide the section
     * @param title the collapsed header; ignored when {@code context} is {@code null}
     */
    public void show(final @Nullable ContextSnapshot context, final String title) {
        Objects.requireNonNull(title, "title");
        setVisible(context != null);
        setManaged(context != null);
        if (context == null) {
            setText("");
            plain = "";
            sections.getChildren().clear();
            return;
        }
        setText(title);
        plain = plainText(context);
        sections.getChildren().setAll(parts(context));
    }

    /** What Copy puts on the clipboard: the same parts as headed, indented plain text. */
    String plainText() {
        return plain;
    }

    private String counts(final ContextSnapshot context) {
        return messages.get(
                MessageKey.LIVE_CONTEXT_COUNTS,
                context.precedingTargets().size(),
                context.summary() == null ? "no" : "yes",
                context.glossary().size(),
                context.tmHits().size());
    }

    private List<Node> parts(final ContextSnapshot context) {
        final List<Node> parts = new ArrayList<>();
        final List<Node> quotes = context.precedingTargets().stream()
                .<Node>map(target -> text("context-quote", target))
                .toList();
        add(parts, MessageKey.LIVE_CONTEXT_PRECEDING, quotes);
        final String summary = context.summary();
        add(
                parts,
                MessageKey.LIVE_CONTEXT_SUMMARY,
                List.of(
                        summary == null
                                ? text("context-none", messages.get(MessageKey.LIVE_CONTEXT_NO_SUMMARY))
                                : text("context-text", summary)));
        add(parts, MessageKey.LIVE_CONTEXT_NAMES, context.glossary().isEmpty() ? List.of() : List.of(names(context)));
        final List<Node> hits = context.tmHits().stream()
                .<Node>map(hit ->
                        text("context-text", messages.get(MessageKey.LIVE_CONTEXT_HIT, hit.source(), hit.target())))
                .toList();
        add(parts, MessageKey.LIVE_CONTEXT_MEMORY, hits);
        return isEmpty(context) ? List.of(text("context-text", messages.get(MessageKey.LIVE_CONTEXT_EMPTY))) : parts;
    }

    // The summary section stays with the other sections, saying when there is none yet; a draft sent nothing at all
    // gets the one line that says so instead.
    private static boolean isEmpty(final ContextSnapshot context) {
        return context.precedingTargets().isEmpty()
                && context.summary() == null
                && context.glossary().isEmpty()
                && context.tmHits().isEmpty();
    }

    private void add(final List<Node> parts, final MessageKey heading, final List<Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        final Label title = new Label(messages.get(heading));
        title.getStyleClass().add("context-heading");
        final VBox section = new VBox(SPACING, title);
        section.getChildren().addAll(nodes);
        parts.add(section);
    }

    private GridPane names(final ContextSnapshot context) {
        final GridPane grid = new GridPane(GRID_GAP, SPACING);
        grid.getStyleClass().add("context-names");
        final ColumnConstraints term = new ColumnConstraints();
        term.setPercentWidth(TERM_SHARE);
        final ColumnConstraints target = new ColumnConstraints();
        target.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(term, target);
        final List<SnapshotTerm> terms = context.glossary();
        for (int row = 0; row < terms.size(); row++) {
            grid.addRow(row, text("context-term", terms.get(row).term()), rendering(terms.get(row)));
        }
        return grid;
    }

    private Node rendering(final SnapshotTerm term) {
        final String target = term.target();
        final Label label = target == null || target.isBlank()
                ? text("context-none", messages.get(MessageKey.LIVE_CONTEXT_NO_RENDERING))
                : text("context-text", target);
        if (!term.locked()) {
            return label;
        }
        final FontIcon lock = new FontIcon(Feather.LOCK);
        lock.getStyleClass().add("context-lock");
        final String locked = messages.get(MessageKey.LIVE_CONTEXT_LOCKED);
        lock.setAccessibleText(locked);
        Tooltip.install(lock, new Tooltip(locked));
        final HBox cell = new HBox(SPACING, label, lock);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private static Label text(final String styleClass, final String value) {
        final Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private String plainText(final ContextSnapshot context) {
        final StringJoiner text = new StringJoiner("\n\n");
        section(
                text,
                MessageKey.LIVE_CONTEXT_PRECEDING,
                context.precedingTargets().stream()
                        .map(target -> "“" + target + "”")
                        .toList());
        final String summary = context.summary();
        section(text, MessageKey.LIVE_CONTEXT_SUMMARY, summary == null ? List.of() : List.of(summary));
        section(
                text,
                MessageKey.LIVE_CONTEXT_NAMES,
                context.glossary().stream().map(this::termLine).toList());
        section(
                text,
                MessageKey.LIVE_CONTEXT_MEMORY,
                context.tmHits().stream().map(this::hitLine).toList());
        return text.length() == 0 ? messages.get(MessageKey.LIVE_CONTEXT_EMPTY) : text.toString();
    }

    private void section(final StringJoiner text, final MessageKey heading, final List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        final StringJoiner block = new StringJoiner("\n" + INDENT, messages.get(heading) + "\n" + INDENT, "");
        lines.forEach(block::add);
        text.add(block.toString());
    }

    private String termLine(final SnapshotTerm term) {
        final String target = term.target();
        return messages.get(
                MessageKey.LIVE_CONTEXT_TERM,
                term.term(),
                target == null || target.isBlank() ? "none" : target,
                term.locked() ? "locked" : "free");
    }

    private String hitLine(final SnapshotTmHit hit) {
        return messages.get(MessageKey.LIVE_CONTEXT_HIT, hit.source(), hit.target());
    }

    /**
     * The scrolling body: as tall as what it holds at the width it is given, up to {@value #BODY_MAX} pixels, and
     * scrolling inside beyond that.
     *
     * <p>A scroll pane measures its content at the content's own preferred width, which for wrapped text is one long
     * line, so the body asked for a line's height and showed one clipped line. It reports a horizontal content bias
     * instead, so the rows above pass it the width it is laid out at, and measures its content at that width; its
     * minimum is that height, so no parent can squeeze it.
     */
    private static final class ContextBody extends ScrollPane {

        ContextBody(final Region content) {
            super(content);
            setFitToWidth(true);
            setHbarPolicy(ScrollBarPolicy.NEVER);
            setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
            setMinHeight(Region.USE_PREF_SIZE);
            setMaxHeight(Region.USE_PREF_SIZE);
            setFocusTraversable(false);
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefHeight(final double width) {
            final double sides = snappedLeftInset() + snappedRightInset();
            final double inner = width < 0 ? -1 : Math.max(0, width - sides);
            final double content = getContent().prefHeight(inner);
            return Math.min(content + snappedTopInset() + snappedBottomInset(), BODY_MAX);
        }
    }
}
