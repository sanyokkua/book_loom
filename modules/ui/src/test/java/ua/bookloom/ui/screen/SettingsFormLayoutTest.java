package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;

/** Where the Providers form puts its footer: pinned in the card, never inside the part that scrolls. */
class SettingsFormLayoutTest extends SettingsScreenTestBase {

    private Bounds inScene(final String id) {
        return ThemeTestSupport.onFx(() -> {
            final Node node = required(id);
            return node.localToScene(node.getBoundsInLocal());
        });
    }

    // IF Save sat inside the scrolling form, THEN a long form at the smallest window would hide it below the fold.
    @Test
    void footer_newProviderAtTheSmallestWindow_saveIsInsideTheSceneAndOutsideTheScroll() {
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        openSettings();
        onFx(() -> button("settings-provider-add").fire());

        final Bounds save = inScene("settings-form-save");

        assertThat(save.getMaxY()).isLessThanOrEqualTo(scene.getHeight());
        assertThat(save.getMinX()).isGreaterThanOrEqualTo(0);
        assertThat(save.getMaxX()).isLessThanOrEqualTo(scene.getWidth());
        assertThat(isInside(button("settings-form-save"), (ScrollPane) required("settings-form-scroll")))
                .isFalse();
    }

    // IF the form grew past the card, THEN the card would push the footer out instead of scrolling its own body.
    @Test
    void scroll_newProviderAtTheSmallestWindow_theBodyScrollsWhileTheFooterStays() {
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        openSettings();
        onFx(() -> button("settings-provider-add").fire());

        final Bounds footer = inScene("settings-form-save");
        final Bounds scroll = inScene("settings-form-scroll");

        assertThat(scroll.getMaxY()).isLessThanOrEqualTo(footer.getMinY());
    }

    private static boolean isInside(final Node node, final Node ancestor) {
        return ThemeTestSupport.onFx(() -> {
            for (Node up = node; up != null; up = up.getParent()) {
                if (up.equals(ancestor)) {
                    return true;
                }
            }
            return false;
        });
    }
}
