package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;

/**
 * Keeps a switch in step with a held value in both directions, and tells {@code choose} only about a change a person
 * made: a value that arrives from the model moves the switch without being chosen again.
 *
 * <p>The value outlives the switch, so it is observed through a weak listener; this object is kept in the switch's
 * properties, so it lives exactly as long as the switch does.
 */
@Slf4j
public final class ModelSwitch {

    private static final String OWNER_KEY = "bookloom.modelSwitch";

    private final ToggleSwitch toggle;
    private final BooleanProperty value;
    private final Consumer<Boolean> choose;
    private final ChangeListener<Boolean> onValue = (observed, was, now) -> show(now);
    private final ChangeListener<Boolean> onSelection = (observed, was, now) -> picked(now);

    private ModelSwitch(final ToggleSwitch toggle, final BooleanProperty value, final Consumer<Boolean> choose) {
        this.toggle = toggle;
        this.value = value;
        this.choose = choose;
    }

    /**
     * Wires the switch to the value.
     *
     * @param toggle the switch, which shows the value from now on
     * @param value the value in force
     * @param choose receives the value a person switched the switch to
     */
    public static void bind(final ToggleSwitch toggle, final BooleanProperty value, final Consumer<Boolean> choose) {
        Objects.requireNonNull(toggle, "toggle");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(choose, "choose");
        final ModelSwitch wiring = new ModelSwitch(toggle, value, choose);
        toggle.setSelected(value.get());
        toggle.getProperties().put(OWNER_KEY, wiring);
        toggle.selectedProperty().addListener(wiring.onSelection);
        value.addListener(new WeakChangeListener<>(wiring.onValue));
    }

    private void show(final boolean now) {
        toggle.setSelected(now);
    }

    private void picked(final boolean now) {
        if (now != value.get()) {
            log.debug("switch {} chosen by a person", now);
            choose.accept(now);
        }
    }
}
