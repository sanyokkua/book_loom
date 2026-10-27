package ua.bookloom.ui.state;

import com.google.inject.Provider;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ReviewMode;

/** Which review mode the screens react to: the one a run in progress keeps, else the one the next run would use. */
// Checkstyle parses source before Lombok creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunModes {

    static ReviewMode current(final StateMirror mirror, final Provider<ReviewMode> next) {
        final RunMode running = mirror.runMode().get();
        return running != null ? running.reviewMode() : next.get();
    }
}
