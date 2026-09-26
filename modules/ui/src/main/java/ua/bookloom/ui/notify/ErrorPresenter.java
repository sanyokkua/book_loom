package ua.bookloom.ui.notify;

import ua.bookloom.api.AppError;

/**
 * The blocking surface for a failure: a dialog with its title, message and folded details.
 *
 * <p>An interface so a screen's test can hand it a recording fake. FX Application Thread only.
 */
public interface ErrorPresenter {

    /**
     * Shows a failure that can only be dismissed.
     *
     * @param error the failure; only its title, message and details are ever shown, never its cause
     */
    void present(AppError error);

    /**
     * Shows a failure and, when it is marked retryable, offers to run the action again.
     *
     * @param error the failure; only its title, message and details are ever shown, never its cause
     * @param onRetry what Retry does after the dialog has closed; never run for a non-retryable failure
     */
    void present(AppError error, Runnable onRetry);
}
