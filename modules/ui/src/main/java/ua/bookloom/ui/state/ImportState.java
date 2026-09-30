package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * What the import screen shows. Which variant a book becomes is decided by the inspection's verdict and its language
 * evidence, never by an error code or message text.
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
     * @param card what the inspection and the profile found
     * @param warning what the book's language declaration gives cause to warn about, or {@code null}
     */
    record Detected(BookCard card, @Nullable LanguageWarning warning) implements ImportState {

        /** Rejects a missing card. */
        public Detected {
            Objects.requireNonNull(card, "card");
        }
    }

    /**
     * The book is encrypted by a scheme the application does not decrypt.
     *
     * @param fileName the name of the refused file
     * @param scheme the encryption scheme's name, or {@code null} when it could not be identified
     */
    record DrmBlocked(String fileName, @Nullable String scheme) implements ImportState {

        /** Rejects a missing file name. */
        public DrmBlocked {
            Objects.requireNonNull(fileName, "fileName");
        }
    }

    /**
     * The file is not one of the four formats the application reads.
     *
     * @param fileName the name of the refused file
     * @param detectedType what the file actually is, such as {@code PDF} or {@code Unknown}
     */
    record Unsupported(String fileName, String detectedType) implements ImportState {

        /** Rejects a missing file name or type. */
        public Unsupported {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(detectedType, "detectedType");
        }
    }

    /**
     * A book of a supported format could not be opened.
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
}
