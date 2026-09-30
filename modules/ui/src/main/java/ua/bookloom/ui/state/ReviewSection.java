package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * The part of the run's state that says why a paused run is waiting: the model error it paused on, or the segment a
 * review pause stopped at; and whether a retry of one segment is in flight, which holds the run's Resume.
 *
 * <p>Kept apart from {@link StateMirror} so that class stays a readable size. The properties are read-only; each
 * {@code publish*} method wraps its mutation in {@link Platform#runLater(Runnable)}, the same bridge the mirror uses.
 */
@Slf4j
public final class ReviewSection {

    private final ReadOnlyObjectWrapper<@Nullable AppError> providerError = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable String> reviewPauseSegment = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper retryInFlight = new ReadOnlyBooleanWrapper();

    /**
     * The model error the run is paused on, whatever its code.
     *
     * @return a read-only property holding {@code null} unless the run paused on an error; read on the FX thread
     */
    public ReadOnlyObjectProperty<@Nullable AppError> providerError() {
        return providerError.getReadOnlyProperty();
    }

    /**
     * The segment a review pause stopped at.
     *
     * @return a read-only property holding {@code null} unless the run is paused for review; read on the FX thread
     */
    public ReadOnlyObjectProperty<@Nullable String> reviewPauseSegment() {
        return reviewPauseSegment.getReadOnlyProperty();
    }

    /**
     * Whether a segment retry is waiting for the model. It is not part of a run, so a new run does not clear it.
     *
     * @return a read-only property; read on the FX thread
     */
    public ReadOnlyBooleanProperty retryInFlight() {
        return retryInFlight.getReadOnlyProperty();
    }

    /**
     * Shows that a segment retry began or ended.
     *
     * @param inFlight whether a retry is now waiting for the model
     */
    public void publishRetryInFlight(final boolean inFlight) {
        log.debug("publishing retry in flight: {}", inFlight);
        Platform.runLater(() -> retryInFlight.set(inFlight));
    }

    /**
     * Shows that the run paused on a model error.
     *
     * @param error the non-null error the pause offered for recovery
     */
    public void publishProviderError(final AppError error) {
        Objects.requireNonNull(error, "error");
        log.debug("publishing a pause on error {}", error.code());
        Platform.runLater(() -> providerError.set(error));
    }

    /**
     * Shows the segment a review pause stopped at.
     *
     * @param segmentId the non-null id of the segment the pause names
     */
    public void publishReviewPauseSegment(final String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("publishing a review pause at {}", segmentId);
        Platform.runLater(() -> reviewPauseSegment.set(segmentId));
    }

    /** Withdraws both: the run went on, so neither the error nor the segment describes it any more. */
    public void publishResumed() {
        log.debug("publishing that the pause is over");
        Platform.runLater(this::reset);
    }

    /** Clears every field; runs on the FX thread as part of the mirror's reset for a new run. */
    void reset() {
        providerError.set(null);
        reviewPauseSegment.set(null);
    }
}
