package ua.bookloom.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.notify.ErrorPresenter;

/** A hand-written {@link ErrorPresenter} that records every error handed to it instead of opening a dialog. */
public final class RecordingErrorPresenter implements ErrorPresenter {

    private final List<AppError> presented = new CopyOnWriteArrayList<>();
    private final List<AppError> runFailures = new CopyOnWriteArrayList<>();

    @Override
    public void present(final AppError error) {
        presented.add(error);
    }

    @Override
    public void present(final AppError error, final Runnable onRetry) {
        presented.add(error);
    }

    @Override
    public void presentRunFailure(final AppError error) {
        presented.add(error);
        runFailures.add(error);
    }

    /** The errors handed to {@code presentRunFailure}, which are also in {@link #presented()}. */
    public List<AppError> runFailures() {
        return List.copyOf(runFailures);
    }

    public List<AppError> presented() {
        return List.copyOf(presented);
    }
}
