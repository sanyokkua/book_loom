package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds every FXML controller through the injector, so a controller's constructor dependencies are the
 * application's own singletons rather than whatever a bare loader could reflectively construct.
 *
 * <p>This is the one class in {@code :ui} permitted to consume the {@link Injector} directly (the controller-factory
 * mandate of {@code javafx-ui.md}); everything else takes its collaborators by constructor. A controller is not
 * scoped, so each load gets its own widgets.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class GuiceControllerFactory implements Callback<Class<?>, Object> {

    private final Injector injector;

    @Override
    public Object call(final Class<?> type) {
        Objects.requireNonNull(type, "type");
        log.debug("building controller {}", type.getName());
        return injector.getInstance(type);
    }
}
