package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.Navigator;

/**
 * Where opening a stored project leads the window.
 *
 * @param importer what makes the opened book the window's open one
 * @param navigator where the step is shown
 * @param locks which steps are still locked once the book is open
 */
public record OpenTargets(ImportViewModel importer, Navigator navigator, StepLocks locks) {

    /** Rejects a missing part. */
    @Inject
    public OpenTargets {
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(navigator, "navigator");
        Objects.requireNonNull(locks, "locks");
    }
}
