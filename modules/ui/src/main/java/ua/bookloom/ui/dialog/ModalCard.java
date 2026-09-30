package ua.bookloom.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;

/**
 * A {@link DialogPane} whose footer reads the same on every platform: the platform button order scatters tertiary
 * actions with gaps (macOS puts "other" buttons mid-bar), while the design wants one right-aligned group, cancel then
 * tertiary then the confirming button.
 */
public class ModalCard extends DialogPane {

    private static final String FOOTER_ORDER = "+LHECUAO";

    @Override
    protected Node createButtonBar() {
        final ButtonBar bar = (ButtonBar) super.createButtonBar();
        bar.setButtonOrder(FOOTER_ORDER);
        return bar;
    }
}
