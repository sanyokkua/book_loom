package ua.bookloom.ui.notify;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * How a service tells the person what happened: a short toast for an outcome and the error presenter for a failure.
 *
 * @param toasts says that something was done
 * @param errors shows why something could not be done
 */
public record Reporting(Toasts toasts, ErrorPresenter errors) {

    /** Rejects a missing part. */
    @Inject
    public Reporting {
        Objects.requireNonNull(toasts, "toasts");
        Objects.requireNonNull(errors, "errors");
    }
}
