package ua.bookloom.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Why a failure happened, where its {@link ErrorCode} alone cannot tell a person what to fix.
 *
 * <p>Services word every {@link AppError} in English and know no display language, so the interface words a failure
 * in another language by its code. A full disk and a missing folder share the code {@link ErrorCode#validation} but
 * need different advice; the reason travels in {@link AppError#details()} through {@link SafeDetails#withReason} as
 * a fixed token, never as text, so the interface can choose the advice in the display language.
 */
public enum FailureReason {

    /** The folder a file was to be written into does not exist. */
    FOLDER_MISSING("folder-missing"),

    /** The operating system refused access to the folder or the file. */
    ACCESS_DENIED("access-denied"),

    /** The folder, the file or the whole disk is read-only. */
    READ_ONLY("read-only"),

    /** The disk ran out of free space while the file was written. */
    DISK_FULL("disk-full"),

    /** A text file is not in the UTF-8 encoding it is read in. */
    NOT_UTF8("not-utf8"),

    /** A CSV file's header names columns that are missing, unknown or repeated. */
    CSV_HEADER("csv-header"),

    /**
     * A provider answered with an HTTP success whose body is only an error: the address reaches a server that does not
     * serve this path, most often an OpenAI-compatible address missing its {@code /v1}.
     */
    WRONG_PATH("wrong-path");

    private static final String PREFIX = "reason=";

    private final String token;

    FailureReason(final String token) {
        this.token = token;
    }

    /**
     * The fixed word this reason is written as in a failure's details.
     *
     * @return the token, lower case with hyphens
     */
    public String token() {
        return token;
    }

    /**
     * Reads the reason back from a failure's rendered details.
     *
     * @param details the rendered details, or {@code null}
     * @return the reason if the details start with one, or empty if they do not
     */
    public static Optional<FailureReason> in(final @Nullable String details) {
        if (details == null || !details.startsWith(PREFIX)) {
            return Optional.empty();
        }
        final int end = details.indexOf(',');
        final String found = details.substring(PREFIX.length(), end < 0 ? details.length() : end);
        return Arrays.stream(values())
                .filter(reason -> Objects.equals(reason.token, found))
                .findFirst();
    }
}
