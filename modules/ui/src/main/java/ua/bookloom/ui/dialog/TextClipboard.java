package ua.bookloom.ui.dialog;

/** Where a dialog puts text a person asked to copy; a seam so a test can see what was copied. */
@FunctionalInterface
public interface TextClipboard {

    /**
     * Puts the text on the clipboard. FX thread only.
     *
     * @param text what to copy
     */
    void copy(String text);
}
