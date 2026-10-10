package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.Set;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SectionMemory;

/**
 * The live card of the Translating screen: the model call in flight above the call before it. When the run has ended
 * the first block is the last call it made, and before any call the card says that nothing was sent yet.
 *
 * <p>When a new call pushes the current one back, the two blocks swap roles instead of being drawn again: the block
 * that showed the call becomes the previous one where it stands (moved below the other without leaving the scene) and
 * the other block takes the new call. A tick that only moves the clock changes the clock's text and nothing else.
 *
 * <p>It holds no state of its own; the mirror's calls are observed through a weak listener that this card's field
 * keeps alive, so a card replaced on the next visit is not kept alive by the mirror. Showing logs nothing, as it runs
 * on every publication.
 */
@Slf4j
public final class LiveCallPanel extends VBox {

    private static final double SPACING = 10;
    private static final Set<RunState> ENDED = Set.of(RunState.COMPLETED, RunState.FAILED, RunState.STOPPED);

    private final String id;
    private final ObservableValue<String> currentTitle;
    private final ObservableValue<String> previousTitle;
    private final Label none;
    // Swapped when the blocks change roles; the scene keeps both nodes throughout.
    private LiveCallView current;
    private LiveCallView previous;
    private final ChangeListener<LiveCalls> onCalls = (observed, was, now) -> show(now);

    /**
     * Builds the card and shows the calls the mirror already holds.
     *
     * @param id the card's node id; the blocks are named {@code <id>-current} and {@code <id>-previous}
     * @param calls the mirror's live calls
     * @param state the mirror's run state, which decides whether the first block reads "Current" or "Last"
     * @param sourceName the heading of every source pane, already translated
     * @param targetName the heading of every target pane, already translated
     * @param messages the catalogue the card is worded from
     */
    public LiveCallPanel(
            final String id,
            final ReadOnlyObjectProperty<LiveCalls> calls,
            final ReadOnlyObjectProperty<RunState> state,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(messages, "messages");
        currentTitle = currentTitle(state, messages);
        previousTitle = Bindings.createStringBinding(() -> messages.get(MessageKey.LIVE_CALL_PREVIOUS));
        current = new LiveCallView(id + "-current", currentTitle, sourceName, targetName, messages);
        previous = new LiveCallView(id + "-previous", previousTitle, sourceName, targetName, messages);
        final Label heading = new Label(messages.get(MessageKey.LIVE_TITLE));
        heading.getStyleClass().add("card-title");
        none = new Label(messages.get(MessageKey.LIVE_CALL_NONE));
        none.setId(id + "-none");
        none.getStyleClass().addAll("live-body", "live-placeholder");
        none.setWrapText(true);
        getChildren().addAll(heading, none, current, previous);
        setId(id);
        getStyleClass().add("card");
        log.debug("live call panel {} bound to the mirror", id);
        show(calls.get());
        calls.addListener(new WeakChangeListener<>(onCalls));
    }

    /**
     * Keeps each block's reply and prompt sections open or closed as the person last left them this session.
     *
     * @param memory the session's memory of open sections
     */
    public void rememberSectionsIn(final SectionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        current.rememberSectionsIn(memory);
        previous.rememberSectionsIn(memory);
    }

    private static ObservableValue<String> currentTitle(
            final ReadOnlyObjectProperty<RunState> state, final Messages messages) {
        return Bindings.createStringBinding(
                () -> messages.get(
                        ENDED.contains(state.get()) ? MessageKey.LIVE_CALL_LAST : MessageKey.LIVE_CALL_CURRENT),
                state);
    }

    private void show(final LiveCalls calls) {
        swapIfPushedBack(calls.previous());
        none.setVisible(calls.current() == null);
        none.setManaged(calls.current() == null);
        current.show(calls.current(), calls);
        previous.show(calls.previous(), calls);
    }

    // The call now published as the previous one is the one the current block shows: that block takes the previous
    // role and moves below the other, which takes the new call. toFront reorders without taking it out of the scene.
    private void swapIfPushedBack(final @Nullable CallSnapshot pushedBack) {
        if (pushedBack == null || !current.isShowing(pushedBack.callId())) {
            return;
        }
        final LiveCallView moved = current;
        current = previous;
        previous = moved;
        current.takeRole(id + "-current", currentTitle);
        previous.takeRole(id + "-previous", previousTitle);
        moved.toFront();
    }
}
