package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableStringValue;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a provider is the one the translation run uses, while the run is at work or paused, so Settings does not
 * delete it: a paused run would otherwise be continued, or retried from the review desk, with a provider that no
 * longer exists. FX thread only.
 */
@Slf4j
@Singleton
public final class ProviderInUse {

    private final StateMirror mirror;
    private final TranslationRunner runner;

    /**
     * Creates the check.
     *
     * @param mirror where the run's state is read
     * @param runner which provider the active run uses
     */
    @Inject
    public ProviderInUse(final StateMirror mirror, final TranslationRunner runner) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    /**
     * Follows whether the provider is in use by the run.
     *
     * @param providerId the provider's id; blank for none
     * @return {@code true} while a running, pausing, paused or stopping run uses the provider, {@code false} otherwise
     */
    public BooleanBinding byRun(final ObservableStringValue providerId) {
        Objects.requireNonNull(providerId, "providerId");
        return Bindings.createBooleanBinding(() -> isUsedByRun(providerId.get()), mirror.runState(), providerId);
    }

    private boolean isUsedByRun(final String providerId) {
        final RunState state = mirror.runState().get();
        final boolean atWork =
                switch (state) {
                    case RUNNING, PAUSING, PAUSED, STOPPING -> true;
                    case IDLE, STOPPED, COMPLETED, FAILED -> false;
                };
        final boolean used =
                atWork && runner.activeProviderId().filter(providerId::equals).isPresent();
        log.debug("provider '{}' in use by the run: {} (run {})", providerId, used, state);
        return used;
    }
}
