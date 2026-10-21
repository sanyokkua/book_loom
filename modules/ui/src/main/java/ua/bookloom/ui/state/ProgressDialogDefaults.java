package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The view a progress dialog opens in: Simple unless technical details are asked for. One holder, so the Appearance
 * setting can later drive every dialog through it. FX Application Thread only.
 */
@Singleton
public final class ProgressDialogDefaults {

    private final ObjectProperty<ProgressDialogModel.Mode> initialMode =
            new SimpleObjectProperty<>(ProgressDialogModel.Mode.SIMPLE);

    /** Creates the holder with Simple chosen. */
    @Inject
    public ProgressDialogDefaults() {
        // the property starts at Simple
    }

    /**
     * The view a newly opened progress dialog starts in.
     *
     * @return the property; never holds null
     */
    public ObjectProperty<ProgressDialogModel.Mode> initialMode() {
        return initialMode;
    }
}
