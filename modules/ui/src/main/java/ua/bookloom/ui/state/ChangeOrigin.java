package ua.bookloom.ui.state;

/** Where an added row came from, so the results can tell what needed no model from what the model chose. */
public enum ChangeOrigin {
    /** The model chose the row, or the operation has no text step. */
    MODEL,
    /** The scan of the book's text found the row, with no model. */
    TEXT
}
