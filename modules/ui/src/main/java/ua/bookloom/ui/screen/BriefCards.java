package ua.bookloom.ui.screen;

import java.util.Arrays;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The cards of the brief that nothing reads yet, drawn with the control the reference draws for each choice and
 * switched off.
 *
 * <p>They are switched off rather than left out because the screen is specified to show what the brief will hold, and
 * rather than left on because a control that silently changes nothing is worse than a control that says it is not
 * available. The pre-selected option and the default switch positions are the reference's, so the picture matches it;
 * none of them is read when a run is assembled.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefCards {

    private static final double CARD_SPACING = 12;
    private static final double FIELD_SPACING = 5;
    private static final double PAIR_SPACING = 14;
    private static final double BALANCE_DEFAULT = 55;
    private static final double BALANCE_MAX = 100;
    private static final int VOICE_ROWS = 2;
    private static final int FIRST = 0;
    private static final int MIDDLE = 1;

    /** A card with its heading, and the tag that says nothing reads it yet when {@code unused}. */
    static VBox card(
            final String id,
            final Messages messages,
            final MessageKey title,
            final boolean unused,
            final Node... body) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(title, "title");
        final Label heading = new Label(messages.get(title));
        heading.getStyleClass().add("card-title");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(heading, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (unused) {
            final Label tag = new Label(messages.get(MessageKey.BRIEF_SOON));
            tag.getStyleClass().add("tag-note");
            header.getChildren().add(tag);
        }
        final VBox card = new VBox(CARD_SPACING, header);
        card.getChildren().addAll(body);
        card.setId(id);
        card.getStyleClass().add("card");
        return card;
    }

    /** A label above the control it names, with an optional hint beneath. */
    static VBox field(final Messages messages, final MessageKey label, final Node control, final MessageKey hint) {
        final VBox field = field(messages, label, control);
        field.getChildren().add(hint(messages, hint));
        return field;
    }

    static VBox field(final Messages messages, final MessageKey label, final Node control) {
        final Label name = new Label(messages.get(label));
        name.getStyleClass().add("muted");
        return new VBox(FIELD_SPACING, name, control);
    }

    static Label hint(final Messages messages, final MessageKey key) {
        final Label hint = new Label(messages.get(key));
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        return hint;
    }

    private static TextField disabledInput(final String id, final Messages messages, final MessageKey prompt) {
        final TextField input = new TextField();
        input.setId(id);
        input.setPromptText(messages.get(prompt));
        input.setDisable(true);
        return input;
    }

    static Node tone(final Messages messages) {
        log.debug("building the tone card, every control disabled");
        final TextField genre = disabledInput("brief-tone-genre", messages, MessageKey.BRIEF_TONE_GENRE_PROMPT);
        final TextArea voice = new TextArea();
        voice.setId("brief-tone-voice");
        voice.getStyleClass().add("brief-input");
        voice.setPromptText(messages.get(MessageKey.BRIEF_TONE_VOICE_PROMPT));
        voice.setPrefRowCount(VOICE_ROWS);
        voice.setWrapText(true);
        voice.setDisable(true);
        final TextField audience =
                disabledInput("brief-tone-audience", messages, MessageKey.BRIEF_TONE_AUDIENCE_PROMPT);
        final Node register = segmented(
                "brief-tone-register",
                messages,
                FIRST,
                MessageKey.BRIEF_REGISTER_FORMAL,
                MessageKey.BRIEF_REGISTER_NEUTRAL,
                MessageKey.BRIEF_REGISTER_CASUAL);
        return card(
                "brief-tone-card",
                messages,
                MessageKey.BRIEF_CARD_TONE,
                true,
                field(messages, MessageKey.BRIEF_TONE_GENRE, genre),
                field(messages, MessageKey.BRIEF_TONE_REGISTER, register),
                field(messages, MessageKey.BRIEF_TONE_VOICE, voice),
                field(messages, MessageKey.BRIEF_TONE_AUDIENCE, audience));
    }

    static Node policies(final Messages messages) {
        log.debug("building the policies card, every control disabled");
        final Node names = segmented(
                "brief-policy-names",
                messages,
                MIDDLE,
                MessageKey.BRIEF_OPTION_TRANSLATE,
                MessageKey.BRIEF_OPTION_TRANSLITERATE,
                MessageKey.BRIEF_OPTION_KEEP_ORIGINAL);
        final Node foreign = segmented(
                "brief-policy-foreign",
                messages,
                FIRST,
                MessageKey.BRIEF_OPTION_KEEP_AS_IS,
                MessageKey.BRIEF_OPTION_TRANSLATE,
                MessageKey.BRIEF_OPTION_TRANSLATE_NOTE);
        return card(
                "brief-policies-card",
                messages,
                MessageKey.BRIEF_CARD_POLICIES,
                true,
                field(messages, MessageKey.BRIEF_POLICY_NAMES, names),
                field(messages, MessageKey.BRIEF_POLICY_FOREIGN, foreign, MessageKey.BRIEF_POLICY_FOREIGN_HINT),
                footnotesAndUnits(messages),
                balance(messages));
    }

    private static Node footnotesAndUnits(final Messages messages) {
        final Node footnotes = segmented(
                "brief-policy-footnotes",
                messages,
                FIRST,
                MessageKey.BRIEF_OPTION_TRANSLATE,
                MessageKey.BRIEF_OPTION_KEEP);
        final Node units = segmented(
                "brief-policy-units", messages, FIRST, MessageKey.BRIEF_OPTION_KEEP, MessageKey.BRIEF_OPTION_METRIC);
        final VBox left = field(messages, MessageKey.BRIEF_POLICY_FOOTNOTES, footnotes);
        final VBox right = field(messages, MessageKey.BRIEF_POLICY_UNITS, units);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        return new HBox(PAIR_SPACING, left, right);
    }

    private static Node balance(final Messages messages) {
        final Slider slider = new Slider(0, BALANCE_MAX, BALANCE_DEFAULT);
        slider.setId("brief-policy-balance");
        slider.setDisable(true);
        return field(messages, MessageKey.BRIEF_POLICY_BALANCE, slider, MessageKey.BRIEF_POLICY_BALANCE_HINT);
    }

    static Node alsoTranslate(final Messages messages) {
        log.debug("building the also-translate card, every control disabled");
        final VBox frontmatter = new VBox(
                FIELD_SPACING,
                toggle("brief-aux-frontmatter", messages, MessageKey.BRIEF_AUX_FRONTMATTER, false),
                hint(messages, MessageKey.BRIEF_AUX_FRONTMATTER_HINT));
        return card(
                "brief-aux-card",
                messages,
                MessageKey.BRIEF_CARD_ALSO,
                true,
                toggle("brief-aux-nav", messages, MessageKey.BRIEF_AUX_NAV, true),
                toggle("brief-aux-alt", messages, MessageKey.BRIEF_AUX_ALT, true),
                toggle("brief-aux-metadata", messages, MessageKey.BRIEF_AUX_METADATA, true),
                frontmatter);
    }

    static Node quality(final Messages messages) {
        log.debug("building the quality card, every control disabled");
        final Node dial = segmented(
                "brief-quality",
                messages,
                MIDDLE,
                MessageKey.BRIEF_QUALITY_FAST,
                MessageKey.BRIEF_QUALITY_BALANCED,
                MessageKey.BRIEF_QUALITY_MAX);
        return card(
                "brief-quality-card",
                messages,
                MessageKey.BRIEF_CARD_QUALITY,
                true,
                dial,
                hint(messages, MessageKey.BRIEF_QUALITY_HINT));
    }

    private static ToggleSwitch toggle(
            final String id, final Messages messages, final MessageKey label, final boolean on) {
        final ToggleSwitch toggle = new ToggleSwitch(messages.get(label));
        toggle.setId(id);
        toggle.setSelected(on);
        toggle.setDisable(true);
        return toggle;
    }

    private static SegmentedButton segmented(
            final String id, final Messages messages, final int selected, final MessageKey... options) {
        final ToggleButton[] buttons = Arrays.stream(options)
                .map(option -> new ToggleButton(messages.get(option)))
                .toArray(ToggleButton[]::new);
        final SegmentedButton segmented = new SegmentedButton(buttons);
        segmented.setId(id);
        buttons[selected].setSelected(true);
        segmented.setMaxWidth(Region.USE_PREF_SIZE);
        segmented.setDisable(true);
        return segmented;
    }
}
