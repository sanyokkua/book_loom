package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * Shows a review action the desk refused on the surface its code is assigned: {@code busy} is one warning toast,
 * {@code validation} is the desk's own reason in place, and anything else that needs attention is the error dialog;
 * and confirms a decision the desk took, since the panel moves on from it. FX thread only.
 */
@Slf4j
final class ReviewRefusals {

    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final Consumer<String> inPlace;
    private final Runnable onSaveRefused;

    ReviewRefusals(
            final Toasts toasts,
            final ErrorPresenter errors,
            final Consumer<String> inPlace,
            final Runnable onSaveRefused) {
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.inPlace = Objects.requireNonNull(inPlace, "inPlace");
        this.onSaveRefused = Objects.requireNonNull(onSaveRefused, "onSaveRefused");
    }

    /** Refuses an action before any call, because a run is translating: the same one warning a desk answer gives. */
    void refuseBusy(final String action, final String segmentId) {
        show(action, segmentId, AppError.of(ErrorCode.busy, "Busy", "A run is translating."));
    }

    /** Confirms an accepted or saved segment, which the panel has just moved on from; other actions stay silent. */
    void confirm(final String action, final String locator, final int remaining) {
        switch (action) {
            case "accept" -> toasts.success(MessageKey.REVIEW_ACCEPTED, locator, remaining);
            case "saveEdit" -> toasts.success(MessageKey.REVIEW_SAVED, locator);
            default -> log.debug("review {} of {} needs no confirmation", action, locator);
        }
    }

    void show(final String action, final String segmentId, final AppError error) {
        switch (FailureSurface.of(error.code())) {
            case WARNING_TOAST -> {
                log.warn("review {} of segment {} refused as busy", action, segmentId);
                toasts.warning(MessageKey.REVIEW_BUSY);
            }
            case IN_PLACE -> {
                log.warn("review {} of segment {} refused: {}", action, segmentId, error.code());
                inPlace.accept(error.message());
                if ("saveEdit".equals(action)) {
                    onSaveRefused.run();
                }
            }
            case DIALOG, PROVIDER_ERROR, FLAGGED_SEGMENT -> errors.present(error);
            case STOPPED, SETTINGS_ONLY -> log.debug("review {} answered {}: nothing to show", action, error.code());
        }
    }
}
