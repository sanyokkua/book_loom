/**
 * Choosing and applying the light or dark token block: the operating-system colour scheme behind an injectable seam,
 * the controller that swaps the block on a scene without a restart, and the names of the stylesheet's shared style
 * classes.
 *
 * <p>Everything here touches the scene graph and so runs on the JavaFX Application Thread only. The blocks themselves
 * live in the one stylesheet located by {@link ua.bookloom.ui.Theme}.
 */
@NullMarked
package ua.bookloom.ui.theme;

import org.jspecify.annotations.NullMarked;
