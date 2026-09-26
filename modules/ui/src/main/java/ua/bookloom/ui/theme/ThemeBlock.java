package ua.bookloom.ui.theme;

/** One of the two value blocks the stylesheet declares for the same set of colour roles. */
public enum ThemeBlock {
    /** The light values, declared on the scene root itself. */
    LIGHT,
    /** The dark values, which override the light ones while the root carries the dark style class. */
    DARK
}
