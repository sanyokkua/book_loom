package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.CallLimits;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The Generation area of the settings screen: what each quality setting does (a table that only shows, the same for
 * every model) and the fixed time limits the HTTP client enforces. It holds no setting: thinking and sampling are set
 * per model on the Models tab (design D20).
 */
@Slf4j
public final class GenerationController {

    private static final double GAP = 10;
    private static final double MILLIS_PER_SECOND = 1000.0;

    private final QualityWords words;
    private final Messages messages;
    private final SettingsViewModel settings;
    // The settings outlive this screen; the listeners reach it through weak references.
    private final ChangeListener<Object> onModel = (observed, was, now) -> showQuality();

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

    /**
     * Receives the collaborators the injector owns.
     *
     * @param words what each quality setting means, in the person's words
     * @param messages the catalogue the built nodes are worded from
     * @param settings the chosen model, whose batch size the extra-calls cell names
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public GenerationController(final QualityWords words, final Messages messages, final SettingsViewModel settings) {
        this.words = Objects.requireNonNull(words, "words");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @FXML
    void initialize() {
        log.debug("building the generation area");
        showQuality();
        settings.model().addListener(new WeakChangeListener<>(onModel));
        settings.selectedProviderId().addListener(new WeakChangeListener<>(onModel));
        final TwoColumns pair = new TwoColumns(qualityCard, right, LayoutClass.Width.REGULAR);
        pair.setId("generation-two-columns");
        columns.getChildren().setAll(pair);
        buildLimits();
    }

    private void showQuality() {
        qualityBody.getChildren().setAll(GenerationQualityTable.of(messages, words));
    }

    private void buildLimits() {
        limits.getChildren()
                .addAll(
                        row(
                                "helper",
                                MessageKey.SETTINGS_GENERATION_LIMITS_HELPER,
                                messages.get(
                                        MessageKey.SETTINGS_GENERATION_LIMITS_HELPER_VALUE,
                                        CallLimits.HELPER_TIMEOUT.toSeconds())),
                        row("draft", MessageKey.SETTINGS_GENERATION_LIMITS_DRAFT, byLength()),
                        row("silence", MessageKey.SETTINGS_GENERATION_LIMITS_SILENCE, seconds(CallLimits.STREAM_IDLE)),
                        row(
                                "tries",
                                MessageKey.SETTINGS_GENERATION_LIMITS_TRIES,
                                messages.get(
                                        MessageKey.SETTINGS_GENERATION_LIMITS_TRIES_VALUE, CallLimits.MAX_ATTEMPTS)));
    }

    private String byLength() {
        final double secondsPerToken = CallLimits.PER_EXPECTED_TOKEN.toMillis() / MILLIS_PER_SECOND;
        return messages.get(
                MessageKey.SETTINGS_GENERATION_LIMITS_DRAFT_VALUE,
                secondsPerToken,
                CallLimits.MAX_CHAT_TIMEOUT.toSeconds());
    }

    private String seconds(final Duration limit) {
        return messages.get(MessageKey.SETTINGS_GENERATION_LIMITS_SECONDS, limit.toSeconds());
    }

    private HBox row(final String name, final MessageKey key, final String value) {
        final Label label = Wrapped.wholeLines(new Label(messages.get(key)));
        label.getStyleClass().add("kv-key");
        final Label shown = Wrapped.wholeLines(new Label(value));
        shown.setId("generation-limit-" + name);
        shown.getStyleClass().add("kv-value");
        final HBox line = new HBox(GAP, label, shown);
        line.getStyleClass().add("kv");
        return line;
    }
}
