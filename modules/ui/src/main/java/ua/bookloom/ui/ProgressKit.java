package ua.bookloom.ui;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.ProgressDialogDefaults;
import ua.bookloom.ui.state.ProgressFigures;
import ua.bookloom.ui.state.WorkVisibility;

/**
 * What a progress dialog is built from.
 *
 * @param defaults the view a new dialog opens in
 * @param figures how each dialog gets its own reader of its figures
 * @param work where the layer says whether a dialog is showing
 */
public record ProgressKit(ProgressDialogDefaults defaults, ProgressFigures.Factory figures, WorkVisibility work) {

    /** Rejects a missing part. */
    @Inject
    public ProgressKit {
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(figures, "figures");
        Objects.requireNonNull(work, "work");
    }
}
