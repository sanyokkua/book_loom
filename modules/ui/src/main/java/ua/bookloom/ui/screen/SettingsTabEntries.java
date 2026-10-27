package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.state.AutomationSettings;
import ua.bookloom.ui.state.DetailedLogSwitch;
import ua.bookloom.ui.state.DiagnosticActions;
import ua.bookloom.ui.state.GenerationSettings;
import ua.bookloom.ui.state.ModelRoster;
import ua.bookloom.ui.state.StorageSettings;

/**
 * What entering a settings tab reads again. The models and the stored choices are read on every entry, not once, so a
 * change made meanwhile (a provider saved, a setting changed from the Export screen) shows.
 */
@Slf4j
final class SettingsTabEntries {

    private static final String MODELS = "settings-tab-models";
    private static final String GENERATION = "settings-tab-generation";
    private static final String AUTOMATION = "settings-tab-automation";
    private static final String STORAGE = "settings-tab-storage";

    private final ModelRoster roster;
    private final GenerationSettings generation;
    private final AutomationSettings automation;
    private final StorageSettings storage;
    private final DetailedLogSwitch detailedLog;
    private final DiagnosticActions diagnostics;

    @Inject
    SettingsTabEntries(
            final ModelRoster roster,
            final GenerationSettings generation,
            final AutomationSettings automation,
            final StorageSettings storage,
            final DetailedLogSwitch detailedLog,
            final DiagnosticActions diagnostics) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.automation = Objects.requireNonNull(automation, "automation");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.detailedLog = Objects.requireNonNull(detailedLog, "detailedLog");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /** Reads what the tab with {@code tabId} shows; a tab with nothing stored reads nothing. */
    void entered(final String tabId) {
        switch (Objects.requireNonNull(tabId, "tabId")) {
            case MODELS -> {
                log.debug("models tab opened: reading the models");
                roster.refresh();
            }
            case GENERATION -> {
                log.debug("generation tab opened: reading the stored choices");
                generation.load();
            }
            case AUTOMATION -> {
                log.debug("automation tab opened: reading the stored choices");
                automation.load();
            }
            case STORAGE -> {
                log.debug("storage tab opened: reading the saved work and the log marker");
                diagnostics.clearStatus();
                storage.load();
                detailedLog.load();
            }
            default -> log.debug("tab {} reads nothing on entry", tabId);
        }
    }
}
