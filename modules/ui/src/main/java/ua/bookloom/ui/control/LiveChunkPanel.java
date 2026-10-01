package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.SectionMemory;

/**
 * The "Current chunk (live)" card: the segment decided last above the one in progress.
 *
 * <p>It holds no state of its own; the mirror's rows are observed through a weak listener that this card's field keeps
 * alive, so a card replaced on the next visit is not kept alive by the mirror. The listener logs the segment ids and
 * locators only, never the texts.
 */
@Slf4j
public final class LiveChunkPanel extends VBox {

    private static final double SPACING = 10;

    private final LiveRowView lastDecided;
    private final LiveRowView current;
    private final ChangeListener<LiveRows> onRows = (observed, was, now) -> show(now);

    /**
     * Builds the card and shows the rows the mirror already holds.
     *
     * @param id the card's node id; the rows' nodes are named {@code live-last-*} and {@code live-current-*}
     * @param rows the mirror's live rows
     * @param sourceName the heading of every source pane, already translated
     * @param targetName the heading of every target pane, already translated
     * @param messages the catalogue the badges are worded from
     */
    public LiveChunkPanel(
            final String id,
            final ReadOnlyObjectProperty<LiveRows> rows,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(messages, "messages");
        lastDecided = new LiveRowView("live-last", sourceName, targetName, messages);
        current = new LiveRowView("live-current", sourceName, targetName, messages);
        final Label heading = new Label(messages.get(MessageKey.LIVE_TITLE));
        heading.getStyleClass().add("card-title");
        getChildren().addAll(heading, lastDecided, current);
        setId(id);
        getStyleClass().add("card");
        log.debug("live panel {} bound to the mirror", id);
        show(rows.get());
        rows.addListener(new WeakChangeListener<>(onRows));
    }

    /**
     * Keeps each row's "Context sent to the model" open or closed as the person last left it this session, so it
     * survives the screen being rebuilt on the next visit as well as the rows it shows changing.
     *
     * @param memory the session's memory of open sections
     */
    public void rememberSectionsIn(final SectionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        lastDecided.rememberContextIn(memory);
        current.rememberContextIn(memory);
    }

    private void show(final LiveRows rows) {
        log.debug(
                "live panel rows: decided {}, in progress {}", describe(rows.lastDecided()), describe(rows.current()));
        lastDecided.show(rows.lastDecided());
        current.show(rows.current());
    }

    private static String describe(final @Nullable LiveRow row) {
        return row == null ? "none" : row.segmentId() + " " + row.locator();
    }
}
