package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import ua.bookloom.ui.state.LaunchProject;

/** What the composition root asks of the window once it is shown; {@code ua.bookloom.ui.state} is not exported. */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class WindowLaunch {

    private final LaunchProject launch;

    /** Opens the last project when the setting is on and it is still stored, else leaves Projects showing. FX thread only. */
    public void openLastProject() {
        launch.openAtLaunch();
    }
}
