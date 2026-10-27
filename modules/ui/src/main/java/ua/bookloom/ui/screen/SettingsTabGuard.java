package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.scene.control.Tab;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.LeaveDialog;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.UnsavedSubject;

/**
 * Asks before a move on the settings screen would drop typing: another provider row, another model, another tab. Each
 * form asks about itself in its own words, and a move asks once.
 */
@Slf4j
public final class SettingsTabGuard {

    static final String PROVIDERS_TAB = "settings-tab-providers";
    static final String MODELS_TAB = "settings-tab-models";

    private final ProviderForm providers;
    private final ModelForm models;
    private final LeaveDialog leaveDialog;

    /**
     * Receives the forms the injector owns.
     *
     * @param providers the Providers form
     * @param models the Models form
     * @param leaveDialog the question asked before changes are dropped
     */
    @Inject
    public SettingsTabGuard(final ProviderForm providers, final ModelForm models, final LeaveDialog leaveDialog) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.models = Objects.requireNonNull(models, "models");
        this.leaveDialog = Objects.requireNonNull(leaveDialog, "leaveDialog");
    }

    /** Runs {@code proceed} now, or after the person has said what to do with the provider typing it would drop. */
    void guardProvider(final Runnable proceed) {
        Objects.requireNonNull(proceed, "proceed");
        if (!providers.dirty().get()) {
            proceed.run();
            return;
        }
        final String subject = providers.subject();
        log.debug("unsaved typing in the provider form: asking before leaving it");
        leaveDialog.askUnsaved(
                subject.isEmpty() ? null : UnsavedSubject.provider(subject),
                () -> {
                    providers.discard();
                    proceed.run();
                },
                () -> providers.save(proceed));
    }

    /** Runs {@code proceed} now, or after the person has said what to do with the model changes it would drop. */
    void guardModel(final Runnable proceed) {
        Objects.requireNonNull(proceed, "proceed");
        if (!models.dirty().get()) {
            proceed.run();
            return;
        }
        final String subject = models.subject();
        log.debug("unsaved changes in the model form: asking before leaving them");
        leaveDialog.askUnsaved(
                subject.isEmpty() ? null : UnsavedSubject.model(subject),
                () -> {
                    models.discard();
                    proceed.run();
                },
                () -> models.save(proceed));
    }

    /**
     * Whether leaving {@code tab} would drop changes.
     *
     * @param tab the tab being left
     * @return {@code true} if the form behind it holds unsaved changes
     */
    boolean holdsChanges(final Tab tab) {
        return switch (String.valueOf(tab.getId())) {
            case PROVIDERS_TAB -> providers.dirty().get();
            case MODELS_TAB -> models.dirty().get();
            default -> false;
        };
    }

    /** Asks about the form behind {@code tab}, then runs {@code proceed}; other tabs just proceed. */
    void guardTab(final Tab tab, final Runnable proceed) {
        log.debug("asking about the form of tab {}", tab.getId());
        switch (String.valueOf(tab.getId())) {
            case PROVIDERS_TAB -> guardProvider(proceed);
            case MODELS_TAB -> guardModel(proceed);
            default -> proceed.run();
        }
    }
}
