package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/** The names pass running in the background, for the conformance case of the screen it turns read-only. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HiddenPassPreparation {

    static void hide(final Injector injector) {
        final ActivityTracker tracker = injector.getInstance(ActivityTracker.class);
        onFx(() -> {
            tracker.hide(tracker.begin(ActivityKind.NAMES_PASS, () -> {}).id());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
