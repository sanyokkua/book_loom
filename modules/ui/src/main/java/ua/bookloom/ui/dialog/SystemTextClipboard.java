package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** The operating system's clipboard. */
public final class SystemTextClipboard implements TextClipboard {

    /** Creates the adapter. */
    @Inject
    public SystemTextClipboard() {
        // nothing to prepare: the system clipboard is looked up on each copy
    }

    @Override
    public void copy(final String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
