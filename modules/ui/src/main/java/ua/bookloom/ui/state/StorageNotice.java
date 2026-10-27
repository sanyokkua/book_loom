package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.persistence.StorageInfo;
import ua.bookloom.ui.notify.ToastOccasion;
import ua.bookloom.ui.notify.Toasts;

/**
 * Tells the person, once per launch, that the stored work could not be read by this version and was moved aside, so an
 * empty Projects screen is never a silent loss: the message names the file the old work now lives in.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class StorageNotice {

    private final StorageInfo storage;
    private final Toasts toasts;

    /** Raises the warning when this launch set the stored work aside; does nothing otherwise. FX thread only. */
    public void announceSetAside() {
        final Optional<Path> aside = storage.setAsideAtOpen();
        log.debug("storage set aside at open: {}", aside.isPresent());
        aside.map(Path::getFileName).map(Path::toString).ifPresent(name -> {
            log.warn("the stored work could not be read and was set aside as {}: the person is told", name);
            toasts.raise(ToastOccasion.STORAGE_SET_ASIDE, name);
        });
    }
}
