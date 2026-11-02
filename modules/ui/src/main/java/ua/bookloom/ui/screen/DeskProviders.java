package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Objects;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.TechnicalDetails;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The providers the review desk launcher draws on; providers, because they hold the launcher themselves.
 *
 * @param viewModels the desk's view model
 * @param pauses the review pause, which opens the desk on its segment
 * @param translating what the desk's Pause pauses
 * @param technical the shared technical-details switch
 */
public record DeskProviders(
        Provider<ReviewViewModel> viewModels,
        Provider<ReviewPauseFollower> pauses,
        Provider<TranslatingViewModel> translating,
        Provider<TechnicalDetails> technical) {

    /** Rejects a missing part. */
    @Inject
    public DeskProviders {
        Objects.requireNonNull(viewModels, "viewModels");
        Objects.requireNonNull(pauses, "pauses");
        Objects.requireNonNull(translating, "translating");
        Objects.requireNonNull(technical, "technical");
    }
}
