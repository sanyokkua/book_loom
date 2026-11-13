package ua.bookloom.ui;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * What the shell stacks over its frame and opens from the title bar.
 *
 * @param overlays the busy scrim, the toasts and the dialog host stacked over the frame
 * @param runParts the title bar's chip and run status, and the banners over the content
 * @param about the About dialog the title bar opens
 */
public record ShellParts(ShellOverlays overlays, ShellRunParts runParts, AboutDialog about) {

    /** Rejects a missing part. */
    @Inject
    public ShellParts {
        Objects.requireNonNull(overlays, "overlays");
        Objects.requireNonNull(runParts, "runParts");
        Objects.requireNonNull(about, "about");
    }
}
