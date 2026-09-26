package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.ui.theme.ThemeController;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The appearance area of the settings screen: the light / dark / system selector, kept in step with the theme
 * controller in both directions, beside a fixed accent that is only shown.
 *
 * <p>The title-bar toggle also sets the mode, so the selection follows {@link ThemeController#modeProperty()} through a
 * weak listener held by a field here, which lives exactly as long as the nodes that use it.
 */
@Slf4j
public final class AppearanceController {

    private final ThemeController themeController;
    private final ChangeListener<ThemeMode> onMode = (observed, was, now) -> showMode(now);
    private final ChangeListener<Toggle> onSelection = (observed, was, now) -> onToggleSelected(now);

    @FXML
    private SegmentedButton themeSelector;

    @FXML
    private ToggleButton lightToggle;

    @FXML
    private ToggleButton darkToggle;

    @FXML
    private ToggleButton systemToggle;

    /**
     * Receives the collaborator the injector owns.
     *
     * @param themeController the holder of the theme mode the selector shows and sets
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public AppearanceController(final ThemeController themeController) {
        this.themeController = Objects.requireNonNull(themeController, "themeController");
    }

    @FXML
    void initialize() {
        log.debug("building the appearance area, mode {}", themeController.mode());
        toggleOf(themeController.mode()).setSelected(true);
        themeSelector.getToggleGroup().selectedToggleProperty().addListener(onSelection);
        themeController.modeProperty().addListener(new WeakChangeListener<>(onMode));
    }

    private void onToggleSelected(final Toggle selected) {
        if (selected == null) {
            // A toggle in a group deselects when clicked again; the mode has not changed, so the choice must still
            // show.
            log.debug("selected option clicked again, keeping mode {}", themeController.mode());
            showMode(themeController.mode());
            return;
        }
        final ThemeMode chosen = modeOf(selected);
        if (chosen == themeController.mode()) {
            log.debug("option for mode {} is already in force", chosen);
        } else {
            log.debug("option for mode {} chosen", chosen);
            themeController.setMode(chosen);
        }
    }

    private void showMode(final ThemeMode mode) {
        final ToggleButton toggle = toggleOf(mode);
        if (!toggle.isSelected()) {
            log.debug("showing mode {} as selected", mode);
            toggle.setSelected(true);
        }
    }

    private ToggleButton toggleOf(final ThemeMode mode) {
        return switch (mode) {
            case LIGHT -> lightToggle;
            case DARK -> darkToggle;
            case SYSTEM -> systemToggle;
        };
    }

    private ThemeMode modeOf(final Toggle selected) {
        return Arrays.stream(ThemeMode.values())
                .filter(mode -> toggleOf(mode).equals(selected))
                .findFirst()
                .orElseThrow();
    }
}
