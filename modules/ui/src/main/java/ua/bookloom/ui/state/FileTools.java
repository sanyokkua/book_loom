package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.dialog.TextClipboard;

/**
 * What a service uses to reach files and the clipboard: the file manager, the file dialog and the clipboard.
 *
 * @param revealer opens a folder or a file in the file manager
 * @param chooser asks where a file goes or which one to take
 * @param clipboard where a copied text is put
 */
public record FileTools(FileRevealer revealer, FileChooserPort chooser, TextClipboard clipboard) {

    /** Rejects a missing part. */
    @Inject
    public FileTools {
        Objects.requireNonNull(revealer, "revealer");
        Objects.requireNonNull(chooser, "chooser");
        Objects.requireNonNull(clipboard, "clipboard");
    }
}
