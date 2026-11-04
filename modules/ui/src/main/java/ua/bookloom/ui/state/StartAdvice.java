package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.beans.value.ObservableValue;
import org.jspecify.annotations.Nullable;

/**
 * The advice for the model a run will use and the open project's language pair, for the lines before Start. Like the
 * quant chip it follows the chosen model and the brief, and reads the model's facts once for each pair when a line
 * that shows them is built; it never blocks Start.
 */
@Singleton
public final class StartAdvice {

    private final ObservableValue<@Nullable AdviceReading> reading;

    /**
     * Follows the chosen model and the open project.
     *
     * @param reader where readings come from
     * @param current the open project, whose brief holds the language pair
     * @param settings the chosen provider and model
     */
    @Inject
    public StartAdvice(final AdviceReader reader, final CurrentProject current, final SettingsViewModel settings) {
        this.reading =
                AdviceFollow.of(reader, current, settings::selection, settings.model(), settings.selectedProviderId());
    }

    /** {@return the reading for the model chosen for the run, or null while there is none; FX thread only} */
    public ObservableValue<@Nullable AdviceReading> reading() {
        return reading;
    }
}
