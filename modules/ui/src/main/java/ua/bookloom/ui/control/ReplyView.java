package ua.bookloom.ui.control;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;
import ua.bookloom.ui.dialog.SystemTextClipboard;
import ua.bookloom.ui.dialog.TextClipboard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A model call's reply as a person reads it: for a batch, a row per item with the segment it stands for, the text that
 * came back and what the reply did with it, so a lost id reads "missing" and not as a gap in a block of JSON; and
 * always the reply exactly as received in a collapsed raw block with Copy (open at once when there are no parsed items).
 * Hidden until the call is answered. Showing logs nothing, as it is redrawn whenever a live call changes.
 */
public final class ReplyView extends CopyablePane {

    private static final String NONE = "—";
    private static final String PROBLEM_JOIN = ", ";
    private static final double SPACING = 8;
    private static final double[] WIDTHS = {8, 20, 47, 25};

    private final VBox content;
    private final RawBlock rawBlock;
    private @Nullable String reply;
    private List<CallItem> items = List.of();
    private Map<String, String> locators = Map.of();
    private boolean wasItemless;

    /**
     * Builds a hidden, collapsed view; {@link #show} fills it.
     *
     * @param id the view's node id; Copy is {@code <id>-copy}, the raw reply is {@code <id>-raw} and the grid is
     *     {@code <id>-grid}
     * @param messages the catalogue the headings are worded from
     */
    public ReplyView(final String id, final Messages messages) {
        this(id, messages, new SystemTextClipboard());
    }

    ReplyView(final String id, final Messages messages, final TextClipboard clipboard) {
        this(id, messages, clipboard, new VBox(SPACING));
    }

    private ReplyView(final String id, final Messages messages, final TextClipboard clipboard, final VBox content) {
        super(
                id,
                messages,
                clipboard,
                MessageKey.LIVE_CALL_REPLY_TIP,
                MessageKey.LIVE_CALL_REPLY_COPY,
                MessageKey.LIVE_CALL_REPLY_COPY_TIP,
                new ScrollingBody(content));
        this.content = content;
        content.getStyleClass().add("context-sections");
        this.rawBlock = new RawBlock(
                id + "-raw",
                messages,
                clipboard,
                this::plainText,
                this::isExpanded,
                MessageKey.LIVE_CALL_REPLY_TIP,
                MessageKey.LIVE_CALL_REPLY_COPY,
                MessageKey.LIVE_CALL_REPLY_COPY_TIP);
        expandedProperty().addListener((observed, was, now) -> rawBlock.refill());
        hideSection();
    }

    /**
     * Shows the reply, or hides the view when the call has none.
     *
     * @param received the reply as received, or null until the call is answered
     * @param parsed what the reply held for each expected id of a batch, or empty for any other call
     * @param segmentLocators the segment's location for a segment id, for the segments the call is about
     */
    public void show(
            final @Nullable String received, final List<CallItem> parsed, final Map<String, String> segmentLocators) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(segmentLocators, "segmentLocators");
        if (Objects.equals(received, reply) && parsed.equals(items) && segmentLocators.equals(locators)) {
            return;
        }
        reply = received;
        items = parsed;
        locators = segmentLocators;
        render();
    }

    @Override
    void rename(final String id) {
        super.rename(id);
        rawBlock.rename(id + "-raw");
        render();
    }

    @Override
    String plainText() {
        final String received = reply;
        return received == null ? "" : received;
    }

    private void render() {
        if (reply == null) {
            hideSection();
            content.getChildren().clear();
            return;
        }
        final boolean itemless = items.isEmpty();
        showSection(
                itemless
                        ? messages().get(MessageKey.LIVE_CALL_REPLY)
                        : messages().get(MessageKey.LIVE_CALL_REPLY_ITEMS, items.size()));
        content.getChildren().setAll(itemless ? List.of() : List.of(grid()));
        rawBlock.display(messages().get(MessageKey.LIVE_CALL_REPLY_RAW), true);
        content.getChildren().add(rawBlock);
        if (itemless != wasItemless) {
            rawBlock.setExpanded(itemless);
            wasItemless = itemless;
        }
    }

    private ParsedGrid grid() {
        final ParsedGrid grid = new ParsedGrid(
                getId() + "-grid",
                List.of(
                        messages().get(MessageKey.LIVE_CALL_COL_ID),
                        messages().get(MessageKey.LIVE_CALL_COL_SEGMENT),
                        messages().get(MessageKey.LIVE_CALL_COL_RETURNED),
                        messages().get(MessageKey.LIVE_CALL_COL_RESULT)),
                WIDTHS);
        for (final CallItem item : items) {
            final String segment = item.segmentId() == null ? NONE : locators.getOrDefault(item.segmentId(), NONE);
            final String target = item.target() == null || item.target().isBlank() ? NONE : item.target();
            grid.addRow(List.of(item.id(), segment, target, resultOf(item)), "reply-result");
        }
        return grid;
    }

    private String resultOf(final CallItem item) {
        final String word = messages().get(statusKey(item.status()));
        return item.problems().isEmpty() ? word : word + " · " + String.join(PROBLEM_JOIN, item.problems());
    }

    private static MessageKey statusKey(final CallItemStatus status) {
        return switch (status) {
            case OK -> MessageKey.LIVE_CALL_ITEM_OK;
            case MISSING -> MessageKey.LIVE_CALL_ITEM_MISSING;
            case DUPLICATE -> MessageKey.LIVE_CALL_ITEM_DUPLICATE;
            case EXTRA -> MessageKey.LIVE_CALL_ITEM_EXTRA;
            case MERGED_SUSPECT -> MessageKey.LIVE_CALL_ITEM_MERGED;
        };
    }
}
