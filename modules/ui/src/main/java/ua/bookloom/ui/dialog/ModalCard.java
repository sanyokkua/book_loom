package ua.bookloom.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;

/**
 * A {@link DialogPane} whose footer reads the same on every platform: the platform button order scatters tertiary
 * actions with gaps (macOS puts "other" buttons mid-bar), while the design wants one right-aligned group, cancel then
 * tertiary then the confirming button.
 */
public class ModalCard extends DialogPane {

    private static final String FOOTER_ORDER = "+LHECUAO";

    // ButtonBar sizes every button to the widest by default, and a card of fixed width then squeezes them all below
    // their words ("Not stated — s…"); each button keeps its own width instead, never less than its words.
    @Override
    protected Node createButton(final ButtonType buttonType) {
        final Node button = super.createButton(buttonType);
        ButtonBar.setButtonUniformSize(button, false);
        if (button instanceof Region region) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }
        return button;
    }

    @Override
    protected Node createButtonBar() {
        final ButtonBar bar = (ButtonBar) super.createButtonBar();
        bar.setButtonOrder(FOOTER_ORDER);
        return bar;
    }
}
