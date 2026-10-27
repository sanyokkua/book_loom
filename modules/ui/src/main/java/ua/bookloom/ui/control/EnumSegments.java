package ua.bookloom.ui.control;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps a row of exclusive toggle buttons in step with a value, in both directions: the selected button shows the
 * value, and choosing another button hands its value to {@code choose}. A toggle in a group deselects when it is
 * clicked again, so a deselection is answered by selecting the current value's button again.
 *
 * <p>The value is held elsewhere and outlives the buttons, so it is observed through a weak listener; this object is
 * kept in the group's properties, so it lives exactly as long as the buttons do.
 *
 * @param <E> the values the buttons stand for
 */
@Slf4j
public final class EnumSegments<E extends Enum<E>> {

    private static final String OWNER_KEY = "bookloom.enumSegments";

    private final ToggleGroup group;
    private final Map<E, ToggleButton> toggles;
    private final ObservableValue<E> current;
    private final Consumer<E> choose;
    private final ChangeListener<E> onValue = (observed, was, now) -> show(now);
    private final ChangeListener<Toggle> onSelection = (observed, was, now) -> picked(now);

    /**
     * Wires the buttons to the value.
     *
     * @param group the group the buttons belong to
     * @param toggles the button of each value
     * @param current the value in force, which the selected button shows
     * @param choose receives the value of a button a person chose
     */
    public EnumSegments(
            final ToggleGroup group,
            final Map<E, ToggleButton> toggles,
            final ObservableValue<E> current,
            final Consumer<E> choose) {
        this.group = Objects.requireNonNull(group, "group");
        this.toggles = Map.copyOf(Objects.requireNonNull(toggles, "toggles"));
        this.current = Objects.requireNonNull(current, "current");
        this.choose = Objects.requireNonNull(choose, "choose");
        show(current.getValue());
        group.getProperties().put(OWNER_KEY, this);
        group.selectedToggleProperty().addListener(onSelection);
        current.addListener(new WeakChangeListener<>(onValue));
    }

    private void picked(final Toggle selected) {
        if (selected == null) {
            log.debug("selected option clicked again, keeping {}", current.getValue());
            show(current.getValue());
            return;
        }
        toggles.entrySet().stream()
                .filter(entry -> entry.getValue().equals(selected))
                .map(Map.Entry::getKey)
                .filter(value -> value != current.getValue())
                .findFirst()
                .ifPresent(value -> {
                    log.debug("option {} chosen", value);
                    choose.accept(value);
                });
    }

    private void show(final E value) {
        final ToggleButton toggle = toggles.get(value);
        if (toggle != null && !toggle.isSelected()) {
            group.selectToggle(toggle);
        }
    }
}
