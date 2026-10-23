package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.api.llm.CallLimits;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.ui.control.EnumSegments;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.GenerationSettings;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TechnicalDetails;

/**
 * The Generation area of the settings screen: what each quality setting does (a table that only shows), the thinking
 * level and the sampling mode (stored the moment they are chosen) and the fixed time limits. A running translation reads
 * the settings once when it starts, so a note says a change reaches the segments not translated yet.
 *
 * <p>The state outlives this controller, so it is observed through weak listeners and bindings that hold it weakly.
 */
@Slf4j
public final class GenerationController {

    private static final double STACK_BELOW = 760;
    private static final double GAP = 10;

    private final GenerationSettings settings;
    private final StateMirror mirror;
    private final TechnicalDetails technicalDetails;
    private final DialProfiles profiles;
    private final Messages messages;

    @FXML
    private Label running;

    @FXML
    private Label saveError;

    @FXML
    private VBox columns;

    @FXML
    private VBox qualityCard;

    @FXML
    private VBox qualityBody;

    @FXML
    private VBox right;

    @FXML
    private VBox limits;

    @FXML
    private SegmentedButton thinking;

    @FXML
    private ToggleButton thinkingOff;

    @FXML
    private ToggleButton thinkingLow;

    @FXML
    private ToggleButton thinkingOn;

    @FXML
    private Label thinkingTech;

    @FXML
    private Label thinkingOverride;

    @FXML
    private SegmentedButton sampling;

    @FXML
    private ToggleButton samplingDefaults;

    @FXML
    private ToggleButton samplingTuned;

    @FXML
    private Label samplingOverride;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param settings the stored thinking and sampling choices
     * @param mirror where the run's state is read
     * @param technicalDetails whether technical names are shown
     * @param profiles what each quality setting means
     * @param messages the catalogue the built nodes are worded from
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public GenerationController(
            final GenerationSettings settings,
            final StateMirror mirror,
            final TechnicalDetails technicalDetails,
            final DialProfiles profiles,
            final Messages messages) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.technicalDetails = Objects.requireNonNull(technicalDetails, "technicalDetails");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @FXML
    void initialize() {
        log.debug("building the generation area");
        qualityBody.getChildren().setAll(GenerationQualityTable.of(messages, profiles));
        final TwoColumns pair = new TwoColumns(qualityCard, right, STACK_BELOW);
        pair.setId("generation-two-columns");
        columns.getChildren().setAll(pair);
        wireThinking();
        wireSampling();
        buildLimits();
        bindNotes();
    }

    private void wireThinking() {
        final Map<ReasoningSetting, ToggleButton> toggles = new EnumMap<>(ReasoningSetting.class);
        toggles.put(ReasoningSetting.OFF, thinkingOff);
        toggles.put(ReasoningSetting.LOW, thinkingLow);
        toggles.put(ReasoningSetting.ON, thinkingOn);
        new EnumSegments<>(thinking.getToggleGroup(), toggles, settings.reasoningProperty(), settings::choose);
        Tips.install(messages, thinkingOff, MessageKey.SETTINGS_GENERATION_THINKING_OFF_TIP);
        Tips.install(messages, thinkingLow, MessageKey.SETTINGS_GENERATION_THINKING_LOW_TIP);
        Tips.install(messages, thinkingOn, MessageKey.SETTINGS_GENERATION_THINKING_ON_TIP);
    }

    private void wireSampling() {
        final Map<SamplingSetting, ToggleButton> toggles = new EnumMap<>(SamplingSetting.class);
        toggles.put(SamplingSetting.PROGRAM_DEFAULTS, samplingDefaults);
        toggles.put(SamplingSetting.TUNED, samplingTuned);
        new EnumSegments<>(sampling.getToggleGroup(), toggles, settings.samplingProperty(), settings::choose);
        Tips.install(messages, samplingDefaults, MessageKey.SETTINGS_GENERATION_SAMPLING_DEFAULTS_TIP);
        Tips.install(messages, samplingTuned, MessageKey.SETTINGS_GENERATION_SAMPLING_TUNED_TIP);
    }

    private void buildLimits() {
        limits.getChildren()
                .addAll(
                        row("check", MessageKey.SETTINGS_GENERATION_LIMITS_CHECK, seconds(CallLimits.REVIEW_TIMEOUT)),
                        row("helper", MessageKey.SETTINGS_GENERATION_LIMITS_HELPER, seconds(CallLimits.HELPER_TIMEOUT)),
                        row(
                                "draft",
                                MessageKey.SETTINGS_GENERATION_LIMITS_DRAFT,
                                messages.get(
                                        MessageKey.SETTINGS_GENERATION_LIMITS_DRAFT_VALUE,
                                        CallLimits.MAX_CHAT_TIMEOUT.toMinutes())),
                        row(
                                "tries",
                                MessageKey.SETTINGS_GENERATION_LIMITS_TRIES,
                                messages.get(
                                        MessageKey.SETTINGS_GENERATION_LIMITS_TRIES_VALUE, CallLimits.MAX_ATTEMPTS)));
    }

    private String seconds(final Duration limit) {
        return messages.get(MessageKey.SETTINGS_GENERATION_LIMITS_SECONDS, limit.toSeconds());
    }

    private HBox row(final String name, final MessageKey key, final String value) {
        final Label label = new Label(messages.get(key));
        label.getStyleClass().add("kv-key");
        final Label shown = new Label(value);
        shown.setId("generation-limit-" + name);
        shown.getStyleClass().add("kv-value");
        shown.setWrapText(true);
        final HBox line = new HBox(GAP, label, shown);
        line.getStyleClass().add("kv");
        return line;
    }

    private void bindNotes() {
        SettingsNotes.show(running, SettingsNotes.runUnderWay(mirror));
        SettingsNotes.show(saveError, settings.saveFailedProperty());
        SettingsNotes.show(thinkingTech, technicalDetails.property());
        SettingsNotes.overrideNote(
                messages,
                thinkingOverride,
                settings.reasoningOverrideProperty(),
                MessageKey.SETTINGS_GENERATION_OVERRIDE);
        SettingsNotes.overrideNote(
                messages,
                samplingOverride,
                settings.samplingOverrideProperty(),
                MessageKey.SETTINGS_GENERATION_OVERRIDE);
    }
}
