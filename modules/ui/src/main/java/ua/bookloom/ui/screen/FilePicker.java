package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.FileChooserPort;
import ua.bookloom.ui.state.FileRevealer;

/**
 * How a screen reaches files.
 *
 * @param chooser asks where a file goes or which one to take
 * @param revealer opens a folder or a file in the file manager
 */
public record FilePicker(FileChooserPort chooser, FileRevealer revealer) {

    /** Rejects a missing part. */
    @Inject
    public FilePicker {
        Objects.requireNonNull(chooser, "chooser");
        Objects.requireNonNull(revealer, "revealer");
    }
}
