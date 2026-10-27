package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import lombok.extern.slf4j.Slf4j;

/**
 * The banners the shell pins between the toolbar and the scrolling content, in the order they were first published.
 *
 * <p>Code that owns a banner publishes it here under a key of its own and withdraws it when it no longer applies; the
 * shell shows whatever the list holds and takes no room while it is empty. Every method must run on the JavaFX
 * Application Thread, because the shell binds its slot to the list.
 */
@Slf4j
@Singleton
public final class ShellBanners {

    private final ObservableList<Node> banners = FXCollections.observableArrayList();
    private final ObservableList<Node> view = FXCollections.unmodifiableObservableList(banners);
    private final ObservableList<String> keys = FXCollections.observableArrayList();

    /**
     * Shows a banner in the slot, in place of the one the same key showed before.
     *
     * @param key who owns the banner, such as {@code translating}; a second publish under it replaces the first
     * @param banner the node to pin
     */
    public void publish(final String key, final Node banner) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(banner, "banner");
        final int at = keys.indexOf(key);
        if (at < 0) {
            log.debug("banner {} pinned", key);
            keys.add(key);
            banners.add(banner);
        } else if (!banners.get(at).equals(banner)) {
            log.debug("banner {} replaced", key);
            banners.set(at, banner);
        }
    }

    /**
     * Takes a banner out of the slot.
     *
     * @param key the key it was published under; a key that shows nothing changes nothing
     */
    public void withdraw(final String key) {
        Objects.requireNonNull(key, "key");
        final int at = keys.indexOf(key);
        if (at >= 0) {
            log.debug("banner {} withdrawn", key);
            keys.remove(at);
            banners.remove(at);
        }
    }

    /**
     * The pinned banners, for the shell's slot to follow.
     *
     * @return a live list, first published first; change it only through {@link #publish} and {@link #withdraw}
     */
    public ObservableList<Node> banners() {
        return view;
    }
}
