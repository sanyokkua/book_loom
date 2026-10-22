package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;

/**
 * Says that a names pass ended in the background, at the moment it ends, whatever screen shows: a message offers
 * "Review the results", and pressing it takes the person to Names &amp; style and opens them once.
 *
 * <p>A singleton created with the injector, because the pass can end while Names &amp; style is not built; a listener
 * of that screen would only announce it on the next visit.
 */
@Slf4j
@Singleton
public final class HiddenResultsAnnouncer {

    private final NamesStyleViewModel names;
    private final HiddenWork hiddenWork;
    private final Navigator navigator;
    // Strongly held: the view model keeps its listeners for as long as it lives, which is the whole run of the app.
    private final ChangeListener<@Nullable ChangeResults> onResults = (observed, was, now) -> ended(now);

    /**
     * Starts following the pass's end.
     *
     * @param names the view model whose results appear when an operation ends
     * @param hiddenWork where the message is raised
     * @param navigator where "Review the results" leads
     */
    @Inject
    public HiddenResultsAnnouncer(
            final NamesStyleViewModel names, final HiddenWork hiddenWork, final Navigator navigator) {
        this.names = Objects.requireNonNull(names, "names");
        this.hiddenWork = Objects.requireNonNull(hiddenWork, "hiddenWork");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        names.results().addListener(onResults);
    }

    private void ended(final @Nullable ChangeResults results) {
        if (results == null || !results.endedHidden()) {
            return;
        }
        if (names.resultsReview().announce(results)) {
            log.debug("the results of {} wait: the pass ended in the background", results.operation());
            hiddenWork.finished(ActivityKind.NAMES_PASS, () -> asked(results));
        }
    }

    private void asked(final ChangeResults results) {
        log.debug("the person asked for the results of {}", results.operation());
        names.resultsReview().requested().set(results);
        navigator.navigate(ViewNames.NAMES_STYLE);
    }
}
