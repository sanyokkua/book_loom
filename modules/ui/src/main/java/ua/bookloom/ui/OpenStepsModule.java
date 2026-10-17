package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.StepLocks;

/**
 * Replaces the step locks with open ones, for the packaged image's resolve-all smoke only: with no book open the real
 * locks refuse most screens, and that smoke exists to load every screen, not to honour the gating. It is applied as
 * an override of {@link UiModule} and never installed in a normal run.
 */
public final class OpenStepsModule extends AbstractModule {

    /** Created by the composition root when the smoke property is set. */
    public OpenStepsModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(StepLocks.class)
                .toInstance(step -> new ReadOnlyObjectWrapper<@Nullable MessageKey>().getReadOnlyProperty());
    }
}
