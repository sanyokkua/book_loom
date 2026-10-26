package ua.bookloom.ui;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.control.ActivityChip;
import ua.bookloom.ui.control.RunBanner;
import ua.bookloom.ui.control.RunStatusBar;
import ua.bookloom.ui.state.ShellBanners;

/**
 * The parts of the shell that follow the run and other model work: the title bar's chip and run status, and the
 * banners pinned between the toolbar and the content.
 *
 * @param activityChip the other model work under way, with its Stop, placed before the run's status
 * @param runStatus the run's status and control, placed between the product name and the theme control
 * @param banners the banners pinned between the toolbar and the content
 * @param runBanner the run's own banner; held so it is built with the shell, as it pins itself in {@code banners}
 */
record ShellRunParts(ActivityChip activityChip, RunStatusBar runStatus, ShellBanners banners, RunBanner runBanner) {

    @Inject
    ShellRunParts {
        Objects.requireNonNull(activityChip, "activityChip");
        Objects.requireNonNull(runStatus, "runStatus");
        Objects.requireNonNull(banners, "banners");
        Objects.requireNonNull(runBanner, "runBanner");
    }
}
