package ua.bookloom.ui.theme;

/** What decides the block in force: an explicit choice, or the operating system. */
public enum ThemeMode {
    /** Always the light block, whatever the operating system prefers. */
    LIGHT,
    /** Always the dark block, whatever the operating system prefers. */
    DARK,
    /** The block the operating system reports when the mode is set; light when it reports no preference. */
    SYSTEM
}
