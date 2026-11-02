package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Objects;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SettingsRepository;

/**
 * Where an export keeps its choices and the last written book.
 *
 * @param stored the stored settings, only read
 * @param store keeps the last written export
 * @param clock stamps the written export
 */
public record ExportStorage(SettingsRepository stored, ProjectRepository store, Clock clock) {

    /** Rejects a missing part. */
    @Inject
    public ExportStorage {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(clock, "clock");
    }
}
