package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * Shows a review action the desk refused on the surface its code is assigned: {@code busy} is one warning toast,
 * {@code validation} is the desk's own reason in place, and anything else that needs attention is the error dialog.
 * FX thread only.
 */
@Slf4j
final class ReviewRefusals {

    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final Consumer<String> inPlace;

    ReviewRefusals(final Toasts toasts, final ErrorPresenter errors, final Consumer<String> inPlace) {
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.inPlace = Objects.requireNonNull(inPlace, "inPlace");
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
            }
            case DIALOG, PROVIDER_ERROR, FLAGGED_SEGMENT -> errors.present(error);
            case STOPPED, SETTINGS_ONLY -> log.debug("review {} answered {}: nothing to show", action, error.code());
        }
    }
}
