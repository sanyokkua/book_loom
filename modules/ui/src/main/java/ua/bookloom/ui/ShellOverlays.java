package ua.bookloom.ui;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.notify.ToastStack;

/**
 * The layers the shell stacks over its frame, bottom to top after the frame: the scrim of work the window waits for,
 * the transient messages, and the dialogs.
 *
 * @param busyHost the layer that shows the work the window waits for
 * @param toasts the transient-message surface, placed over the content area
 * @param modalHost the overlay every dialog is shown in
 */
record ShellOverlays(BusyHost busyHost, ToastStack toasts, ModalHost modalHost) {

    @Inject
    ShellOverlays {
        Objects.requireNonNull(busyHost, "busyHost");
        Objects.requireNonNull(toasts, "toasts");
        Objects.requireNonNull(modalHost, "modalHost");
    }
}
