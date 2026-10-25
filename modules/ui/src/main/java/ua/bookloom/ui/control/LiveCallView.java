package ua.bookloom.ui.control;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SectionMemory;

/**
 * The call of the selected row in the live card's Detailed view, as it was actually sent: its token figures, the
 * message of a call about no segment, then the filled parts of the prompt in the order they were sent beside the reply
 * as received (or what is known while none has come).
 *
 * <p>Its clock is not here: the card's call line carries the one clock. The body is updated only when the call changed,
 * so a tick that moves the clock lays nothing out. Showing logs nothing, as it runs on every publication; the calls it
 * shows are logged where they are worked out.
 */
public final class LiveCallView extends VBox {

    private static final double SPACING = 8;

    private final Messages messages;
    private final Label tokens = new FigureLabel();
    private final InputPane input;
    private final Label replyNote = new Label();
    private final ReplyView reply;
    private final PromptView prompt;
    private @Nullable CallSnapshot shownCall;

    /**
     * Builds a hidden block.
     *
     * @param id the block's node id; its parts are named {@code <id>-tokens}, {@code -input}, {@code -reply-note},
     *     {@code -reply} and {@code -prompt}
     * @param messages the catalogue the block is worded from
     */
    public LiveCallView(final String id, final Messages messages) {
        super(SPACING);
        Objects.requireNonNull(id, "id");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.input = new InputPane(id + "-input", messages);
        this.reply = new ReplyView(id + "-reply", messages);
        this.prompt = new PromptView(id + "-prompt", messages);
        setId(id);
        getStyleClass().add("live-call");
        tokens.setId(id + "-tokens");
        tokens.getStyleClass().add("muted");
        replyNote.setId(id + "-reply-note");
        replyNote.getStyleClass().addAll("live-body", "live-placeholder");
        replyNote.setWrapText(true);
        replyNote.setMinHeight(Region.USE_PREF_SIZE);
        final VBox replyColumn = new VBox(SPACING, replyNote, reply);
        // Prompt | reply side by side, stacked in a compact window (L6), as the mockup's pair.
        getChildren().addAll(tokens, input, new TwoColumns(prompt, replyColumn, LayoutClass.Width.REGULAR));
        show(null);
    }

    /**
     * Keeps the reply and the prompt sections open or closed as the person last left them this session.
     *
     * @param memory the session's memory of open sections
     */
    public void rememberSectionsIn(final SectionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        reply.rememberIn(memory);
        prompt.rememberIn(memory);
    }

    /**
     * Shows a call, or hides the block when there is none.
     *
     * @param call the call, or {@code null} to hide the block
     */
    public void show(final @Nullable CallSnapshot call) {
        setVisible(call != null);
        setManaged(call != null);
        if (call == null || call.equals(shownCall)) {
            shownCall = call;
            return;
        }
        shownCall = call;
        final TokenUsage usage = call.usage();
        tokens.setText(usage == null ? "" : tokensText(usage));
        tokens.setVisible(usage != null);
        tokens.setManaged(usage != null);
        input.show(call.segments().isEmpty() ? call.sent() : null);
        showReplyNote(call);
        reply.show(call.reply(), call.items(), segmentLocators(call));
        prompt.show(call.sections(), call.sent(), itemLocators(call));
    }

    private String tokensText(final TokenUsage usage) {
        final Integer in = usage.prompt();
        final Integer out = usage.completion();
        return messages.get(
                MessageKey.LIVE_CALL_TOKENS,
                in == null ? "none" : in.toString(),
                out == null ? "none" : out.toString());
    }

    // A call that has not answered says what it is doing, so the reply's place is never silently empty.
    private void showReplyNote(final CallSnapshot call) {
        final boolean noteShown = call.reply() == null;
        replyNote.setVisible(noteShown);
        replyNote.setManaged(noteShown);
        if (noteShown) {
            replyNote.setText(messages.get(
                    call.state() == CallState.WAITING
                            ? MessageKey.LIVE_CALL_REPLY_WAITING
                            : MessageKey.LIVE_CALL_REPLY_NONE));
        }
    }

    private static Map<String, String> segmentLocators(final CallSnapshot call) {
        return call.segments().stream()
                .collect(Collectors.toMap(CallSegment::id, CallSegment::locator, (first, second) -> first));
    }

    // A batch numbers its items from one in segment order; once the reply is read, the ids it names are exact.
    private static Map<String, String> itemLocators(final CallSnapshot call) {
        final Map<String, String> byId = segmentLocators(call);
        final Map<String, String> found = new HashMap<>();
        for (int index = 0; index < call.segments().size(); index++) {
            found.put(String.valueOf(index + 1), call.segments().get(index).locator());
        }
        for (final CallItem item : call.items()) {
            final String locator = item.segmentId() == null ? null : byId.get(item.segmentId());
            if (locator != null) {
                found.put(item.id(), locator);
            }
        }
        return found;
    }
}
