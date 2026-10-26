package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.control.EnumSegments;
import ua.bookloom.ui.control.ModelSwitch;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.AutomationSettings;
import ua.bookloom.ui.state.StateMirror;

/**
 * The Automation area of the settings screen: when a translation stops and asks, the quality new books start at, and the
 * four switches. Every choice is stored the moment it is made; a running translation keeps the way it stops and asks, so
 * a note says the change reaches the next one that starts.
 *
 * <p>The state outlives this controller, so it is observed through weak listeners and bindings that hold it weakly.
 */
@Slf4j
public final class AutomationController {

    private final AutomationSettings settings;
    private final StateMirror mirror;
    private final Messages messages;

    @FXML
    private Label running;

    @FXML
    private Label saveError;

    @FXML
    private SegmentedButton review;

    @FXML
    private ToggleButton reviewUnattended;

    @FXML
    private ToggleButton reviewAssisted;

    @FXML
    private ToggleButton reviewManual;

    @FXML
    private Label reviewOverride;

    @FXML
    private SegmentedButton quality;

    @FXML
    private ToggleButton qualityFast;

    @FXML
    private ToggleButton qualityBalanced;

    @FXML
    private ToggleButton qualityMax;

    @FXML
    private ToggleSwitch openDesk;

    @FXML
    private ToggleSwitch lastCheck;

    @FXML
    private Label lastCheckHint;

    @FXML
    private ToggleSwitch keepAwake;

    @FXML
    private ToggleSwitch reopen;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param settings the stored automation choices
     * @param mirror where the run's state is read
     * @param messages the catalogue the built nodes are worded from
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public AutomationController(final AutomationSettings settings, final StateMirror mirror, final Messages messages) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @FXML
    void initialize() {
        log.debug("building the automation area");
        wireReview();
        wireQuality();
        wireSwitches();
        bindNotes();
    }

    private void wireReview() {
        final Map<ReviewMode, ToggleButton> toggles = new EnumMap<>(ReviewMode.class);
        toggles.put(ReviewMode.UNATTENDED, reviewUnattended);
        toggles.put(ReviewMode.ASSISTED, reviewAssisted);
        toggles.put(ReviewMode.MANUAL, reviewManual);
        new EnumSegments<>(review.getToggleGroup(), toggles, settings.reviewModeProperty(), settings::chooseReviewMode);
        Tips.install(messages, reviewUnattended, MessageKey.SETTINGS_AUTOMATION_REVIEW_UNATTENDED_TIP);
        Tips.install(messages, reviewAssisted, MessageKey.SETTINGS_AUTOMATION_REVIEW_ASSISTED_TIP);
        Tips.install(messages, reviewManual, MessageKey.SETTINGS_AUTOMATION_REVIEW_MANUAL_TIP);
    }

    private void wireQuality() {
        final Map<QualityDial, ToggleButton> toggles = new EnumMap<>(QualityDial.class);
        toggles.put(QualityDial.FAST, qualityFast);
        toggles.put(QualityDial.BALANCED, qualityBalanced);
        toggles.put(QualityDial.MAX, qualityMax);
        new EnumSegments<>(quality.getToggleGroup(), toggles, settings.defaultDialProperty(), settings::chooseDial);
        Tips.install(messages, qualityFast, MessageKey.SETTINGS_AUTOMATION_QUALITY_FAST_TIP);
        Tips.install(messages, qualityBalanced, MessageKey.SETTINGS_AUTOMATION_QUALITY_BALANCED_TIP);
        Tips.install(messages, qualityMax, MessageKey.SETTINGS_AUTOMATION_QUALITY_MAX_TIP);
    }

    private void wireSwitches() {
        ModelSwitch.bind(openDesk, settings.openReviewDeskProperty(), settings::chooseOpenReviewDesk);
        ModelSwitch.bind(lastCheck, settings.lastCheckProperty(), settings::chooseLastCheck);
        ModelSwitch.bind(keepAwake, settings.keepAwakeProperty(), settings::chooseKeepAwake);
        ModelSwitch.bind(reopen, settings.reopenLastProjectProperty(), settings::chooseReopenLastProject);
        Tips.install(messages, openDesk, MessageKey.SETTINGS_AUTOMATION_OPEN_DESK_TIP);
        Tips.install(messages, lastCheck, MessageKey.SETTINGS_AUTOMATION_LAST_CHECK_TIP);
        Tips.install(messages, keepAwake, MessageKey.SETTINGS_AUTOMATION_KEEP_AWAKE_TIP);
        Tips.install(messages, reopen, MessageKey.SETTINGS_AUTOMATION_REOPEN_TIP);
    }

    private void bindNotes() {
        Visibility.bind(running, SettingsNotes.runUnderWay(mirror));
        Visibility.bind(saveError, settings.saveFailedProperty());
        Visibility.bind(
                lastCheckHint,
                settings.loadedProperty().and(settings.lastCheckChosenProperty().not()));
        SettingsNotes.overrideNote(
                messages,
                reviewOverride,
                settings.reviewModeOverrideProperty(),
                MessageKey.SETTINGS_AUTOMATION_REVIEW_OVERRIDE);
    }
}
