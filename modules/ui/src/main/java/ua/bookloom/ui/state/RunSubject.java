package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * What a run is started on.
 *
 * @param current the open book a run is started on
 * @param settings where the provider and model come from
 */
public record RunSubject(CurrentProject current, SettingsViewModel settings) {

    /** Rejects a missing part. */
    @Inject
    public RunSubject {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(settings, "settings");
    }
}
