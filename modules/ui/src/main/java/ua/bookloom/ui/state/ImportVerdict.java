package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * The one line the import page says about what it found.
 *
 * @param kind which look and which controls the line takes
 * @param text the sentence, already worded in the interface language
 */
public record ImportVerdict(Kind kind, String text) {

    /** What the line is about: its role decides the colour and which controls sit beside it. */
    public enum Kind {
        /** A book is being read; the line carries a bar and Cancel. */
        READING,
        /** The book can be translated. */
        READY,
        /** The book can be translated, but its details and its chapters name two languages. */
        TWO_MARKS,
        /** The book can be translated, but the language it declares is not one the application names. */
        UNRECOGNIZED,
        /** The file is locked by a shop's encryption. */
        DRM,
        /** The file is not one of the formats the application reads. */
        UNSUPPORTED,
        /** A readable format could not be read. */
        DAMAGED,
        /** The book opened, but holds no text to translate. */
        NO_TEXT,
        /** The same book is already stored as one or more projects. */
        ALREADY
    }

    /** Rejects a missing kind or text. */
    public ImportVerdict {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }
}
