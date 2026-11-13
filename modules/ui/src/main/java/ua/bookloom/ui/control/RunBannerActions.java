package ua.bookloom.ui.control;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.RunInterventions;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * What the run banner's buttons do.
 *
 * @param viewModel what Try again now resumes
 * @param interventions what Skip asks to flag the segments in flight
 * @param mirror the failure Show details opens
 * @param errors where Show details opens it
 */
public record RunBannerActions(
        TranslatingViewModel viewModel, RunInterventions interventions, StateMirror mirror, ErrorPresenter errors) {

    /** Rejects a missing part. */
    @Inject
    public RunBannerActions {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(interventions, "interventions");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(errors, "errors");
    }
}
