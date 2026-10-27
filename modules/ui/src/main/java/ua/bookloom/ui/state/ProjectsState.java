package ua.bookloom.ui.state;

/** What the Projects screen is showing. */
public enum ProjectsState {
    /** The stored projects are being read. */
    LOADING,
    /** At least one project is stored. */
    LIST,
    /** Nothing is stored: the first-launch card. */
    EMPTY,
    /** The projects could not be listed; the error is shown in its own dialog. */
    FAILED
}
