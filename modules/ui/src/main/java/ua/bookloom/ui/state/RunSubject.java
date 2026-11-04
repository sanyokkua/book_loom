package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * What a run is started on.
 *
 * @param current the open book a run is started on
 * @param settings where the provider and model come from
 * @param readyQuantChip the quant chip of the chosen model, which follows it from the first moment
 */
public record RunSubject(CurrentProject current, SettingsViewModel settings, ReadyQuantChip readyQuantChip) {

    /** Rejects a missing part. */
    public RunSubject {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(readyQuantChip, "readyQuantChip");
    }

    /**
     * Builds the chip beside the two parts it follows.
     *
     * @param current the open book a run is started on
     * @param settings where the provider and model come from
     */
    @Inject
    public RunSubject(final CurrentProject current, final SettingsViewModel settings) {
        this(current, settings, new ReadyQuantChip(Objects.requireNonNull(settings, "settings")));
    }
}
