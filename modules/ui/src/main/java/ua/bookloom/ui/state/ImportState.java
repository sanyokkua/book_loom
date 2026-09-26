package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.AppError;

/**
 * What the import screen shows. The language-mismatch variant exists because the screen is specified to carry it, but
 * nothing detects a source language yet, so {@link ImportViewModel#open} never produces it.
 */
public sealed interface ImportState {

    /** Nothing has been chosen, or the last attempt failed for a reason shown elsewhere. */
    record Idle() implements ImportState {}

    /**
     * A book is being parsed.
     *
     * @param fileName the name of the file being opened
     */
    record Opening(String fileName) implements ImportState {

        /** Rejects a missing file name. */
        public Opening {
            Objects.requireNonNull(fileName, "fileName");
        }
    }

    /**
     * A book was opened and is reported.
     *
     * @param card what the parse found
     */
    record Detected(BookCard card) implements ImportState {

        /** Rejects a missing card. */
        public Detected {
            Objects.requireNonNull(card, "card");
        }
    }

    /**
     * The file was refused for a reason the person can act on.
     *
     * @param fileName the name of the refused file
     * @param error the typed refusal the port returned
     */
    record Refused(String fileName, AppError error) implements ImportState {

        /** Rejects a missing file name or error. */
        public Refused {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * The language the book declares disagrees with the language its text is in.
     *
     * @param card what the parse found; it must declare a language, since there is no disagreement without one
     * @param detectedLang the language the text is in
     */
    record LanguageMismatch(BookCard card, String detectedLang) implements ImportState {

        /** Rejects a missing card or language, or a card that declares no language. */
        public LanguageMismatch {
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(detectedLang, "detectedLang");
            Objects.requireNonNull(card.declaredLang(), "card.declaredLang");
        }

        /**
         * The language the book declares.
         *
         * @return the card's declared language, which the constructor guarantees is present
         */
        public String declaredLang() {
            return Objects.requireNonNull(card.declaredLang(), "card.declaredLang");
        }
    }
}
