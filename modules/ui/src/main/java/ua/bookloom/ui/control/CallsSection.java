package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveCalls;

/**
 * The folded "Model calls" section of a card whose work asks the model: the current call and the one before it, shown
 * the way the Translating screen's live panel shows them. It is hidden while there is no call. Showing logs nothing, as
 * a waiting call's clock redraws it every second.
 */
public final class CallsSection extends TitledPane {

    private final LiveCallView current;
    private final LiveCallView previous;

    /**
     * Builds a folded, hidden section.
     *
     * @param id the section's node id
     * @param callId the node id of the current call's block; the previous call's is {@code callId + "-previous"}
     * @param messages the catalogue the section is worded from
     */
    public CallsSection(final String id, final String callId, final Messages messages) {
        current = callView(callId, MessageKey.LIVE_CALL_CURRENT, messages);
        previous = callView(callId + "-previous", MessageKey.LIVE_CALL_PREVIOUS, messages);
        setText(messages.get(MessageKey.BUSY_CALLS));
        setContent(new VBox(current, previous));
        setId(id);
        setExpanded(false);
        Tips.install(messages, this, MessageKey.BUSY_CALLS_TIP);
        show(LiveCalls.EMPTY);
    }

    /**
     * Shows the calls; the section is hidden when there is no current call.
     *
     * @param live the current and previous call
     */
    public void show(final LiveCalls live) {
        Objects.requireNonNull(live, "live");
        final @Nullable CallSnapshot now = live.current();
        setVisible(now != null);
        setManaged(now != null);
        current.show(now, live);
        previous.show(live.previous(), live);
    }

    /**
     * Redraws only the current call, for a waiting call's moving clock.
     *
     * @param now the call in flight
     * @param live the calls with the moment its clock reads
     */
    public void showCurrent(final CallSnapshot now, final LiveCalls live) {
        current.show(now, live);
    }

    private static LiveCallView callView(final String id, final MessageKey heading, final Messages messages) {
        return new LiveCallView(
                id,
                new ReadOnlyStringWrapper(messages.get(heading)),
                new ReadOnlyStringWrapper(messages.get(MessageKey.LIVE_SOURCE_FALLBACK)),
                new ReadOnlyStringWrapper(messages.get(MessageKey.LIVE_TARGET_FALLBACK)),
                messages);
    }
}
