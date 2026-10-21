package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The view a progress dialog opens in: Simple unless technical details are asked for. One holder, so the Appearance
 * setting can later drive every dialog through it. FX Application Thread only.
 */
@Singleton
public final class ProgressDialogDefaults {

    private final ObjectProperty<ProgressDialogModel.Mode> initialMode = new SimpleObjectProperty<>();

    /**
     * Creates the holder; the mode follows the switch from now on, and a dialog already open keeps the mode it has.
     *
     * @param technical the "Show technical details" switch
     */
    @Inject
    public ProgressDialogDefaults(final TechnicalDetails technical) {
        Objects.requireNonNull(technical, "technical");
        initialMode.set(modeFor(technical.isOn()));
        technical.property().addListener((observable, was, now) -> initialMode.set(modeFor(now)));
    }

    private static ProgressDialogModel.Mode modeFor(final boolean technical) {
        return technical ? ProgressDialogModel.Mode.DETAILED : ProgressDialogModel.Mode.SIMPLE;
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
