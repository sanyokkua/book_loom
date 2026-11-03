package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ObservableValue;
import org.jspecify.annotations.Nullable;

/**
 * The quant chip of the model chosen for a run. It starts from the model's name as soon as the model or its provider
 * changes, so the ready card has a chip even when Settings was never opened.
 */
final class ReadyQuantChip {

    private final SettingsViewModel settings;
    private final ObjectBinding<@Nullable QuantChipModel> chip;

    ReadyQuantChip(final SettingsViewModel settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.chip = Bindings.createObjectBinding(
                () -> settings.quantChips()
                        .chipFor(
                                settings.selectedProviderId().get(),
                                settings.model().get().strip()),
                settings.selectedProviderId(),
                settings.model(),
                settings.quantChips().chips());
        settings.model().addListener(observed -> readChosenName());
        settings.selectedProviderId().addListener(observed -> readChosenName());
        readChosenName();
    }

    ObservableValue<@Nullable QuantChipModel> chip() {
        return chip;
    }

    private void readChosenName() {
        final String chosen = settings.model().get().strip();
        if (!chosen.isEmpty()) {
            settings.quantChips().fromNames(settings.selectedProviderId().get(), List.of(chosen));
        }
    }
}
