package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.StepLocks;

/** Every workflow step open, whatever the book and its languages. */
final class OpenStepsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(StepLocks.class)
                .toInstance(step -> new ReadOnlyObjectWrapper<@Nullable MessageKey>().getReadOnlyProperty());
    }
}
