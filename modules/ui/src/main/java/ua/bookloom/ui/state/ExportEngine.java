package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.pipeline.ExportService;

/**
 * What writes the book.
 *
 * @param exports builds and runs the export job
 * @param models creates the model the consistency pass asks
 * @param settings the chosen provider and model
 */
public record ExportEngine(ExportService exports, ChatModelFactory models, SettingsViewModel settings) {

    /** Rejects a missing part. */
    @Inject
    public ExportEngine {
        Objects.requireNonNull(exports, "exports");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(settings, "settings");
    }
}
