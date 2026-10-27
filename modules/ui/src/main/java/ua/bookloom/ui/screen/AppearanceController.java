package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.ui.ExitGuard;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.LanguageChoice;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.SystemLanguage;
import ua.bookloom.ui.state.AppLanguage;
import ua.bookloom.ui.state.TechnicalDetails;
import ua.bookloom.ui.theme.ThemeController;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The appearance area of the settings screen: the light / dark / system selector, kept in step with the theme
 * controller in both directions, the language choice with its restart, and the technical-details switch.
 *
 * <p>The title-bar toggle also sets the mode, so the selection follows {@link ThemeController#modeProperty()} through a
 * weak listener held by a field here, which lives exactly as long as the nodes that use it.
 */
@Slf4j
public final class AppearanceController {

    private final ThemeController themeController;
    private final Messages messages;
    private final AppLanguage appLanguage;
    private final ExitGuard exitGuard;
    private final SystemLanguage system;
    private final TechnicalDetails technicalDetails;
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

    @FXML
    private ComboBox<LanguageChoice> language;

    @FXML
    private Button restart;

    @FXML
    private Label restartError;

    @FXML
    private Label saveError;

    @FXML
    private ToggleSwitch technical;

    /**
     * Receives the collaborator the injector owns.
     *
     * @param themeController the holder of the theme mode the selector shows and sets
     * @param messages the catalogue the hover explanations are drawn from
     * @param appLanguage the language choice and its restart
     * @param exitGuard asks before the window closes with work under way
     * @param system the language of the operating system, named in the follow-the-computer option
     * @param technicalDetails the one switch for technical lines
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public AppearanceController(
            final ThemeController themeController,
            final Messages messages,
            final AppLanguage appLanguage,
            final ExitGuard exitGuard,
            final SystemLanguage system,
            final TechnicalDetails technicalDetails) {
        this.themeController = Objects.requireNonNull(themeController, "themeController");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.appLanguage = Objects.requireNonNull(appLanguage, "appLanguage");
        this.exitGuard = Objects.requireNonNull(exitGuard, "exitGuard");
        this.system = Objects.requireNonNull(system, "system");
        this.technicalDetails = Objects.requireNonNull(technicalDetails, "technicalDetails");
    }

    @FXML
    void initialize() {
        log.debug("building the appearance area, mode {}", themeController.mode());
        Tips.install(messages, lightToggle, MessageKey.APPEARANCE_THEME_LIGHT_TIP);
        Tips.install(messages, darkToggle, MessageKey.APPEARANCE_THEME_DARK_TIP);
        Tips.install(messages, systemToggle, MessageKey.APPEARANCE_THEME_SYSTEM_TIP);
        toggleOf(themeController.mode()).setSelected(true);
        themeSelector.getToggleGroup().selectedToggleProperty().addListener(onSelection);
        themeController.modeProperty().addListener(new WeakChangeListener<>(onMode));
        new AppearanceLanguageRow(
                appLanguage,
                exitGuard,
                messages,
                system,
                language,
                restart,
                restartError,
                saveError,
                themeController.saveFailedProperty());
        Tips.install(messages, technical, MessageKey.APPEARANCE_TECHNICAL_TIP);
        technical.selectedProperty().bindBidirectional(technicalDetails.property());
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
