package ua.bookloom.api.project;

/** Who chose a project's name: the application from the file name, or the person. */
public enum NameOrigin {
    /** Derived from the book's file name; the person has not named the project. */
    AUTO,
    /** Typed by the person; a second project of the same book and language pair is then allowed. */
    USER
}
