package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
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
     * @param detectedType what the file actually is, such as {@code PDF}, or {@code null} when its type is not known,
     *     which the line words without naming one
     */
    record Unsupported(String fileName, @Nullable String detectedType) implements ImportState {

        /** Rejects a missing file name. */
        public Unsupported {
            Objects.requireNonNull(fileName, "fileName");
        }
    }

    /**
     * The book opened, but its body holds no word to translate, so no project was made for it.
     *
     * @param fileName the name of the refused file
     */
    record NoText(String fileName) implements ImportState {

        /** Rejects a missing file name. */
        public NoText {
            Objects.requireNonNull(fileName, "fileName");
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

    /**
     * The chosen file's content is already stored as one or more projects, so nothing was imported: importing it again
     * would reset a stored project's segments.
     *
     * @param fileName the name of the chosen file
     * @param source the chosen file
     * @param projectNames the display names of the stored projects holding this content, in list order; at least one
     * @param bookTitle the title of the first stored project's book, which "Show them" searches for
     */
    record AlreadyInProjects(String fileName, Path source, List<String> projectNames, String bookTitle)
            implements ImportState {

        /** Rejects a missing part and copies the names, so the state cannot change after it is shown. */
        public AlreadyInProjects {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(source, "source");
            projectNames = List.copyOf(projectNames);
            Objects.requireNonNull(bookTitle, "bookTitle");
        }
    }
}
