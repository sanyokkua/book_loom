package ua.bookloom.ui.notify;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.Messages;

/**
 * The {@link ErrorPresenter} that shows a failure as a card in the shell's modal host.
 *
 * <p>The card is shown without dismissal from outside, so a failure is acknowledged through its own buttons; Escape
 * still closes it, which the host guarantees for every card.
 */
@Slf4j
@Singleton
public final class ModalErrorPresenter implements ErrorPresenter {

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the presenter.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public ModalErrorPresenter(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void present(final AppError error) {
        Objects.requireNonNull(error, "error");
        show(error, null);
    }

    @Override
    public void present(final AppError error, final Runnable onRetry) {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(onRetry, "onRetry");
        show(error, onRetry);
    }

    private void show(final AppError error, final @Nullable Runnable onRetry) {
        log.debug(
                "presenting a failure: code={}, retryable={}, hasDetails={}, retryAction={}, surface=modal",
                error.code(),
                error.retryable(),
                ErrorDialog.hasDetails(error),
                onRetry != null);
        final Runnable retry = onRetry == null ? null : () -> retry(onRetry);
        final ErrorDialog dialog = new ErrorDialog(messages, error, retry, this::dismiss);
        modalHost.show(dialog.card(), false);
    }

    private void retry(final Runnable onRetry) {
        log.debug("error dialog answered with Retry");
        modalHost.hide();
        onRetry.run();
    }

    private void dismiss() {
        log.debug("error dialog answered with Dismiss");
        modalHost.hide();
    }
}
