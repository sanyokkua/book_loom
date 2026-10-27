package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.state.ShellBanners;

/**
 * The strip between the toolbar and the scrolling content that shows the banners {@link ShellBanners} holds.
 *
 * <p>It has no height while it is empty: its padding belongs to the {@code filled} style class, which it carries only
 * while a banner is shown. Nothing is logged here; the holder logs what is published.
 */
public final class BannerSlot extends VBox {

    private static final String FILLED = "filled";

    /**
     * Builds the slot and keeps its children equal to the holder's banners.
     *
     * @param banners what is pinned; the slot follows it for as long as it lives
     */
    public BannerSlot(final ShellBanners banners) {
        Objects.requireNonNull(banners, "banners");
        setId("shell-banners");
        getStyleClass().add("shell-banners");
        // The content below gives way, never the banners: a squeezed slot cut a long line to one row.
        setMinHeight(USE_PREF_SIZE);
        final ListChangeListener<Node> follow = change -> {
            getChildren().setAll(banners.banners());
            markFilled(!getChildren().isEmpty());
        };
        banners.banners().addListener(follow);
        follow.onChanged(null);
    }

    private void markFilled(final boolean filled) {
        if (filled != getStyleClass().contains(FILLED)) {
            if (filled) {
                getStyleClass().add(FILLED);
            } else {
                getStyleClass().remove(FILLED);
            }
        }
    }
}
