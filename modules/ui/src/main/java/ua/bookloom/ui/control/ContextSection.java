package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The live row's collapsed "Context sent to the model" section: its header counts what the draft was given, and its
 * body lists the preceding translations as quoted blocks, the running summary, the glossary names and the
 * translation-memory hits, as selectable text that scrolls.
 *
 * <p>It stays collapsed or open as the person left it while the rows it shows change. It logs nothing: it is redrawn on
 * every live-row change.
 */
final class ContextSection extends TitledPane {

    private static final int BODY_ROWS = 8;
    private static final String INDENT = "    ";

    private final Messages messages;
    private final TextArea body = new TextArea();

    ContextSection(final String id, final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        setId(id);
        getStyleClass().add("context-section");
        setExpanded(false);
        setAnimated(false);
        body.setId(id + "-body");
        body.getStyleClass().add("context-body");
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(BODY_ROWS);
        body.setFocusTraversable(false);
        setContent(body);
        Tips.install(messages, this, MessageKey.LIVE_CONTEXT_TIP);
        Tips.install(messages, body, MessageKey.LIVE_CONTEXT_TIP);
        show(null);
    }

    /**
     * Shows what a draft was sent with, or hides the section when nothing was announced.
     *
     * @param context the context, or {@code null} to hide the section
     */
    void show(final @Nullable ContextSnapshot context) {
        setVisible(context != null);
        setManaged(context != null);
        if (context == null) {
            setText("");
            body.setText("");
            return;
        }
        setText(messages.get(MessageKey.LIVE_CONTEXT_TITLE) + " · " + counts(context));
        body.setText(bodyOf(context));
        body.positionCaret(0);
    }

    private String counts(final ContextSnapshot context) {
        return messages.get(
                MessageKey.LIVE_CONTEXT_COUNTS,
                context.precedingTargets().size(),
                context.summary() == null ? "no" : "yes",
                context.glossary().size(),
                context.tmHits().size());
    }

    private String bodyOf(final ContextSnapshot context) {
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
}
