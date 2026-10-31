package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.Objects;
import javafx.scene.control.CheckBox;
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
 * One character of the Start question: its line, a note on where the offered gender comes from, the gender choices with
 * the offered one chosen, and a Confirm tick that ticks itself once the choice is changed. Only a ticked row is the
 * person's answer; an untouched row stays a hint. FX thread only.
 */
final class GenderRow {

    static final String CHOICE_PREFIX = "genders-choice-";
    static final String CONFIRM_PREFIX = "genders-confirm-";

    private static final double GAP = 12;
    private static final double SPACING = 4;
    private static final List<Gender> CHOICES = List.of(Gender.FEMALE, Gender.MALE, Gender.NEUTER, Gender.UNKNOWN);

    private final UnknownGender character;
    private final ToggleGroup group = new ToggleGroup();
    private final CheckBox confirm;
    private final VBox node = new VBox(SPACING);

    GenderRow(final Messages messages, final UnknownGender character, final String line, final int index) {
        Objects.requireNonNull(messages, "messages");
        this.character = Objects.requireNonNull(character, "character");
        node.getChildren().add(Wrapped.label(line, "dialog-text"));
        final String note = noteOf(messages, character);
        if (note != null) {
            node.getChildren().add(Wrapped.label(note, "hint"));
        }
        final FlowPane choices = new FlowPane(GAP, SPACING);
        for (final Gender gender : CHOICES) {
            choices.getChildren().add(choice(messages, gender, index));
        }
        confirm = Tips.install(
                messages,
                new CheckBox(messages.get(MessageKey.DIALOG_GENDERS_ROW_CONFIRM)),
                MessageKey.DIALOG_GENDERS_ROW_CONFIRM_TIP);
        confirm.setId(CONFIRM_PREFIX + index);
        confirm.setMinWidth(Region.USE_PREF_SIZE);
        choices.getChildren().add(confirm);
        node.getChildren().add(choices);
        // Added after the offered choice is made, so only the person's own change ticks the row.
        group.selectedToggleProperty().addListener((observed, old, now) -> confirm.setSelected(true));
    }

    VBox node() {
        return node;
    }

    String entryId() {
        return character.entryId();
    }

    boolean isTicked() {
        return confirm.isSelected();
    }

    Gender chosen() {
        return group.getSelectedToggle() != null && group.getSelectedToggle().getUserData() instanceof Gender gender
                ? gender
                : character.offered();
    }

    private RadioButton choice(final Messages messages, final Gender gender, final int index) {
        final RadioButton radio = Tips.install(
                messages,
                new RadioButton(GlossaryLabels.gender(messages, gender)),
                MessageKey.DIALOG_GENDERS_ROW_CHOICE_HELP);
        radio.setId(CHOICE_PREFIX + gender.name().toLowerCase(java.util.Locale.ROOT) + "-" + index);
        radio.setUserData(gender);
        radio.setToggleGroup(group);
        radio.setSelected(gender == character.offered());
        radio.setMinWidth(Region.USE_PREF_SIZE);
        return radio;
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
