package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.QuantChipModel;

/**
 * The chip that says how well a model's quant keeps translation quality: its token and its tier, in the colour role of
 * the tier and never cut short. A cell reuses one chip and calls {@link #show}, so nothing is allocated or logged
 * while a list scrolls.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuantChip {

    static final String ID = "quant-chip";

    private static final String BASE = "chip";

    /** A chip showing {@code model}. */
    static Label of(final QuantChipModel model, final Messages messages) {
        final Label chip = blank(messages);
        show(chip, model, messages);
        return chip;
    }

    /** A chip with nothing to say yet, for a cell to fill with {@link #show}. */
    static Label blank(final Messages messages) {
        Objects.requireNonNull(messages, "messages");
        final Label chip = new Label();
        chip.setId(ID);
        chip.setWrapText(false);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, chip, MessageKey.SETTINGS_QUANT_CHIP_TIP);
        return chip;
    }

    /** Words and colour role of the chip for {@code model}. */
    static void show(final Label chip, final QuantChipModel model, final Messages messages) {
        final ChipLook look = ChipLook.of(model.tier());
        chip.getStyleClass().setAll(BASE, look.styleClass());
        chip.setText(
                model.tier() == null
                        ? model.token()
                        : messages.get(MessageKey.SETTINGS_QUANT_CHIP, model.token(), messages.get(look.status())));
    }
}
