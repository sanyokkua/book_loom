package ua.bookloom.ui.screen;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * One segmented choice over the values of an enum, always with exactly one segment selected.
 *
 * <p>A segment is never allowed to shrink below its label, because Ukrainian words are long and a label ending in an
 * ellipsis cannot be chosen with confidence. When the labels together are wider than the room the choice is given, each
 * label with more than one word is put on two lines instead, which is how a narrow window still shows every label whole.
 * Pressing the selected segment again does not clear the choice. A value the view model publishes is written with
 * {@link #show} without being handed back as if the person had picked it.
 *
 * @param <E> the choice's value type
 */
@Slf4j
final class BriefChoice<E> {

    /** One segment: the value it stands for and the words on it. */
    record Option<E>(E value, MessageKey label) {}

    private final SegmentedButton node;
    private final Map<E, ToggleButton> buttons = new HashMap<>();
    private final Map<ToggleButton, E> values = new HashMap<>();
    private final Map<ToggleButton, String> labels = new LinkedHashMap<>();
    private final Consumer<E> onPick;
    // True while a value from the view model is written into the segments. FX thread only.
    private boolean applying;

    BriefChoice(final String id, final Messages messages, final List<Option<E>> options, final Consumer<E> onPick) {
        Objects.requireNonNull(id, "id");
        this.onPick = Objects.requireNonNull(onPick, "onPick");
        final ToggleButton[] segments = options.stream()
                .map(option -> segment(messages.get(option.label()), option.value()))
                .toArray(ToggleButton[]::new);
        this.node = new SegmentedButton(segments);
        node.setId(id);
        node.parentProperty().addListener((observed, was, now) -> watchRoom(now));
        node.getToggleGroup().selectedToggleProperty().addListener((observed, was, now) -> onToggled(was, now));
    }

    Node node() {
        return node;
    }

    void show(final E value) {
        final ToggleButton button = buttons.get(value);
        if (button == null) {
            log.debug("choice {} has no segment for {}", node.getId(), value);
            return;
        }
        applying = true;
        try {
            button.setSelected(true);
        } finally {
            applying = false;
        }
    }

    private ToggleButton segment(final String text, final E value) {
        final ToggleButton button = new ToggleButton(text);
        button.setWrapText(true);
        button.setMinWidth(Region.USE_PREF_SIZE);
        buttons.put(value, button);
        values.put(button, value);
        labels.put(button, text);
        return button;
    }

    private void watchRoom(final @Nullable Parent parent) {
        if (parent instanceof Region region) {
            region.widthProperty().addListener((observed, was, now) -> fitTo(now.doubleValue()));
        }
    }

    // Measured with every label on one line, because that is the width the choice needs to show them all.
    private void fitTo(final double room) {
        labels.forEach(ToggleButton::setText);
        if (node.prefWidth(-1) > room) {
            labels.forEach((button, text) -> button.setText(onTwoLines(text)));
            log.debug("choice {} puts its labels on two lines in {} px", node.getId(), room);
        }
    }

    private static String onTwoLines(final String text) {
        final int middle = text.length() / 2;
        int split = -1;
        for (int at = text.indexOf(' '); at >= 0; at = text.indexOf(' ', at + 1)) {
            if (split < 0 || Math.abs(at - middle) < Math.abs(split - middle)) {
                split = at;
            }
        }
        return split < 0 ? text : text.substring(0, split) + "\n" + text.substring(split + 1);
    }

    private void onToggled(final @Nullable Toggle was, final @Nullable Toggle now) {
        if (applying) {
            return;
        }
        if (now == null) {
            if (was == null) {
                return;
            }
            log.debug("choice {} keeps its selection: a segment was pressed twice", node.getId());
            applying = true;
            try {
                was.setSelected(true);
            } finally {
                applying = false;
            }
            return;
        }
        final E value = Objects.requireNonNull(values.get(now), "a segment of this choice");
        log.debug("choice {} picked {}", node.getId(), value);
        onPick.accept(value);
    }
}
