package ua.bookloom.persistence;

import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.AppError;

/**
 * Why the window's database could not be opened, carried through Guice's provisioning so the composition root can
 * show the person the typed failure as a startup message: a Guice provider can only throw, and a startup step that
 * fails before any window exists has no other way to hand its {@link AppError} on.
 */
public final class StorageOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AppError error;

    /**
     * Wraps the failure the database reported.
     *
     * @param error the typed failure; its title and message are shown to the person, its cause is kept as this
     *     exception's cause for the log
     */
    public StorageOpenException(final AppError error) {
        super(Objects.requireNonNull(error, "error").message(), error.cause());
        this.error = error;
    }

    /**
     * The typed failure.
     *
     * @return the failure the database reported
     */
    public AppError error() {
        return error;
    }

    /**
     * Finds the database's failure anywhere in a chain of causes, as Guice wraps it.
     *
     * @param thrown what the injector threw
     * @return the typed failure if the chain holds one, or empty if the failure was something else
     */
    public static Optional<AppError> errorIn(final Throwable thrown) {
        Objects.requireNonNull(thrown, "thrown");
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof StorageOpenException storage) {
                return Optional.of(storage.error());
            }
        }
        return Optional.empty();
    }
}
