package ua.bookloom.ui;

import java.net.URL;
import java.util.Objects;

/**
 * Locates the single theme stylesheet, from inside the module that owns it.
 *
 * <p>This indirection is a JPMS requirement, not a convenience. {@code theme.css} sits at
 * {@code ua/bookloom/ui/theme.css}, a path that maps to a package, and resources in a package are encapsulated to
 * their module unless it is {@code opens}. {@code :app} therefore cannot read it with its own class loader — but a
 * lookup performed <em>here</em>, by a class in {@code :ui}, always succeeds. Opening the package to {@code :app}
 * instead would widen reflective access to every type in it, to solve a problem one method solves.
 *
 * <p>The stylesheet is attached at {@code Scene} level so the {@code .root} token block cascades to every node.
 */
public final class Theme {

    /**
     * The style class that switches the token set to dark. The light values live on {@code .root} itself, and
     * {@code .root.theme-dark} overrides the same role names, so adding this class to the Scene root re-resolves every
     * looked-up colour and removing it restores the light block — one stylesheet, two value blocks, no per-component
     * dark rules.
     */
    public static final String DARK_STYLE_CLASS = "theme-dark";

    private static final String STYLESHEET = "theme.css";

    private Theme() {
        // Static lookup only.
    }

    /**
     * The theme stylesheet's location, for {@code Scene.getStylesheets().add(...)}.
     *
     * @return the stylesheet URL as an external form
     * @throws IllegalStateException if the stylesheet is missing from the packaged module, which would mean the
     *     application had shipped with no theme at all — a build defect worth failing loudly rather than rendering
     *     an unstyled window that looks merely wrong
     */
    public static String stylesheet() {
        final URL url = Objects.requireNonNull(
                Theme.class.getResource(STYLESHEET), "theme.css is missing from the :ui module resources");
        return url.toExternalForm();
    }
}
