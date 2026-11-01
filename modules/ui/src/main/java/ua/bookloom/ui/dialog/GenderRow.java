package ua.bookloom.ui.dialog;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * One character of the Start question: the name with its rendering, one line of mentions and where the offered gender
 * comes from, and the gender choices with the offered one chosen. The radio is the answer: a click on any of them,
 * the offered one too, makes that gender the person's; a row nobody clicked stays a hint. FX thread only.
 */
final class GenderRow {

    static final String CHOICE_PREFIX = "genders-choice-";

    private static final double GAP = 12;
    private static final double SPACING = 4;
    private static final List<Gender> CHOICES = List.of(Gender.FEMALE, Gender.MALE, Gender.NEUTER, Gender.UNKNOWN);

    private final UnknownGender character;
    private final ToggleGroup group = new ToggleGroup();
    private final Messages messages;
    private final Label detail;
    private final RadioButton offeredRadio;
    private boolean answered;
    private final VBox node = new VBox(SPACING);

    GenderRow(final Messages messages, final UnknownGender character, final int index) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.character = Objects.requireNonNull(character, "character");
        node.getChildren().add(Wrapped.label(titleOf(messages, character), "dialog-text"));
        detail = Wrapped.label(detailOf(messages, character, false), "hint");
        node.getChildren().add(detail);
        final FlowPane choices = new FlowPane(GAP, SPACING);
        final Map<Gender, RadioButton> radios = new EnumMap<>(Gender.class);
        for (final Gender gender : CHOICES) {
            final RadioButton radio = choice(messages, gender, index);
            radios.put(gender, radio);
            choices.getChildren().add(radio);
        }
        offeredRadio = Objects.requireNonNull(radios.get(character.offered()), "offered");
        node.getChildren().add(choices);
    }

    VBox node() {
        return node;
    }

    String entryId() {
        return character.entryId();
    }

    boolean isAnswered() {
        return answered;
    }

    boolean isSuggested() {
        return character.suggested();
    }

    /** Answers with the offered gender, as a click on its radio does. */
    void confirmOffered() {
        offeredRadio.fire();
    }

    Gender chosen() {
        return group.getSelectedToggle() != null && group.getSelectedToggle().getUserData() instanceof Gender gender
                ? gender
                : character.offered();
    }

    // A radio that is already selected ignores its own click, so agreeing with the offered gender would say nothing;
    // this one always selects itself and fires, which is what the mouse and the keyboard call.
    private static final class AnswerRadio extends RadioButton {

        AnswerRadio(final String text) {
            super(text);
        }

        @Override
        public void fire() {
            if (!isDisabled()) {
                setSelected(true);
                fireEvent(new ActionEvent());
            }
        }
    }

    private RadioButton choice(final Messages messages, final Gender gender, final int index) {
        final RadioButton radio = Tips.install(
                messages,
                new AnswerRadio(GlossaryLabels.gender(messages, gender)),
                MessageKey.DIALOG_GENDERS_ROW_CHOICE_HELP);
        radio.setId(CHOICE_PREFIX + gender.name().toLowerCase(java.util.Locale.ROOT) + "-" + index);
        radio.setUserData(gender);
        radio.setToggleGroup(group);
        radio.setSelected(gender == character.offered());
        radio.setOnAction(event -> answer());
        radio.setMinWidth(Region.USE_PREF_SIZE);
        return radio;
    }

    private void answer() {
        answered = true;
        detail.setText(detailOf(messages, character, true));
    }

    private static String titleOf(final Messages messages, final UnknownGender character) {
        final String target = character.target();
        return target == null || target.isBlank()
                ? messages.get(MessageKey.DIALOG_GENDERS_ROW_UNTRANSLATED, character.term())
                : messages.get(MessageKey.DIALOG_GENDERS_ROW, character.term(), target);
    }

    // Mentions, then one word on where the offered gender comes from, or that the person has answered.
    private static String detailOf(final Messages messages, final UnknownGender character, final boolean answered) {
        final String note =
                answered ? messages.get(MessageKey.DIALOG_GENDERS_ROW_ANSWERED) : noteOf(messages, character);
        return note == null
                ? messages.get(MessageKey.DIALOG_GENDERS_ROW_MENTIONS, character.mentions())
                : messages.get(MessageKey.DIALOG_GENDERS_ROW_NOTED, character.mentions(), note);
    }

    // The note says where the offered value comes from; a character with nothing offered has none.
    static @Nullable String noteOf(final Messages messages, final UnknownGender character) {
        final Origin origin = character.origin();
        if (origin == Origin.RULE) {
            final Gender model = character.modelGender();
            return model == null
                    ? messages.get(MessageKey.DIALOG_GENDERS_ROW_RULE)
                    : messages.get(
                            MessageKey.DIALOG_GENDERS_ROW_DISAGREE,
                            GlossaryLabels.gender(messages, character.offered()),
                            GlossaryLabels.gender(messages, model));
        }
        if (origin == Origin.LIKELY) {
            final Double confidence = character.confidence();
            return confidence != null && confidence < NameKnowledge.LOW_CONFIDENCE
                    ? messages.get(MessageKey.DIALOG_GENDERS_ROW_LOW_CONFIDENCE)
                    : messages.get(MessageKey.DIALOG_GENDERS_ROW_LIKELY);
        }
        return null;
    }
}
