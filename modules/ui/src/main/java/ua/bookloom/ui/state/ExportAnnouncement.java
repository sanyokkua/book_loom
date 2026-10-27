package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.ExportCompleteDialog;

/**
 * The one announcement of a written book, held by the export view model, which lives for the whole run of the app,
 * because the Export screen is rebuilt on every visit and may not be on show when the export ends (it ran in the
 * background, or the person went elsewhere).
 * On the Export screen the export-complete card waits for a free window; on any other screen a message offers it, and
 * nothing opens by itself. FX thread only.
 */
@Slf4j
@Singleton
public final class ExportAnnouncement implements ExportAnnouncer {

    private final Navigator navigator;
    private final ExportCompleteDialog dialog;
    private final HiddenWork hiddenWork;

    /**
     * Creates the announcer.
     *
     * @param navigator tells which screen is on show and takes the person back to Export
     * @param dialog the card that reports a written book
     * @param hiddenWork where the message is raised when the screen was left
     */
    @Inject
    public ExportAnnouncement(
            final Navigator navigator, final ExportCompleteDialog dialog, final HiddenWork hiddenWork) {
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
        this.hiddenWork = Objects.requireNonNull(hiddenWork, "hiddenWork");
    }

    @Override
    public void written(final ExportOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (navigator.currentView().get() == ViewNames.EXPORT) {
            log.debug("the export ended on the Export screen: the complete card waits for a free window");
            dialog.showWhenFree(outcome);
            return;
        }
        log.debug("the export ended while another screen shows: a message offers the complete card");
        hiddenWork.finished(ActivityKind.EXPORT, () -> asked(outcome));
    }

    private void asked(final ExportOutcome outcome) {
        log.debug("the person asked for the written book's card");
        navigator.navigate(ViewNames.EXPORT);
        dialog.showWhenFree(outcome);
    }
}
