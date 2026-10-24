package ua.bookloom.util.files;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;

/**
 * Classifies a failed write by what the person can do about it.
 *
 * <p>The operating system reports a full disk and a read-only file system only as text ({@code ENOSPC}, {@code EROFS}
 * and their Windows counterparts), so those two are recognised by the system's own wording; the text is read here and
 * never copied into a failure.
 */
// Checkstyle cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileFailures {

    private static final List<String> DISK_FULL_WORDS =
            List.of("no space left on device", "not enough space on the disk", "disk full", "quota exceeded");
    private static final List<String> READ_ONLY_WORDS = List.of("read-only file system", "write-protected");
    private static final List<String> DENIED_WORDS = List.of("permission denied", "operation not permitted");

    /**
     * Tells why a write failed, from the failure or any of its causes.
     *
     * @param failure what the write threw
     * @return the reason if one of the four write failures a person can fix is recognised, or empty if not
     */
    public static Optional<FailureReason> reasonOf(final Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        final Set<Throwable> seen = new HashSet<>();
        for (Throwable at = failure; at != null && seen.add(at); at = at.getCause()) {
            final Optional<FailureReason> reason = reasonOfOne(at);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    /**
     * The failure a person reads for a write that failed for a reason they can fix.
     *
     * @param reason why the write failed
     * @param folder the folder the file was to be written into; only its last name reaches the details
     * @param cause what the write threw, kept for the log only
     * @return a {@link ErrorCode#validation} failure whose message says what to fix
     */
    public static AppError writeError(final FailureReason reason, final Path folder, final Throwable cause) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(folder, "folder");
        final String details =
                SafeDetails.empty().withReason(reason).withFolder(folder).render();
        final Wording words = wordingOf(reason);
        return AppError.of(ErrorCode.validation, words.title(), words.message(), details, cause);
    }

    /**
     * The failure a person reads for a write of {@code target} that failed for a reason they can fix.
     *
     * @param target the file that was being written or put in place
     * @param failure what the write threw
     * @return the failure if the reason is recognised ({@link #reasonOf}), or empty if it is not
     */
    public static Optional<AppError> fixable(final Path target, final Throwable failure) {
        Objects.requireNonNull(target, "target");
        final Path absolute = target.toAbsolutePath();
        final Path folder = absolute.getParent() == null ? absolute : absolute.getParent();
        return reasonOf(failure).map(reason -> writeError(reason, folder, failure));
    }

    /** The English title and message of a write failure; the interface words it again in other languages. */
    private record Wording(String title, String message) {}

    private static Wording wordingOf(final FailureReason reason) {
        return switch (reason) {
            case FOLDER_MISSING ->
                new Wording(
                        "The folder does not exist",
                        "The folder to save into does not exist. Create it or choose another folder.");
            case ACCESS_DENIED ->
                new Wording(
                        "No permission to save here",
                        "BookLoom may not write to this folder. Choose another folder or allow writing to it.");
            case READ_ONLY ->
                new Wording(
                        "This place is read-only",
                        "The folder, the file or the disk is read-only. Choose a folder on a disk you can write to.");
            case DISK_FULL ->
                new Wording(
                        "The disk is full",
                        "There is not enough free space on the disk. Free some space or choose another disk.");
            case NOT_UTF8, CSV_HEADER ->
                new Wording("This file could not be written", "The file could not be written.");
        };
    }

    private static Optional<FailureReason> reasonOfOne(final Throwable failure) {
        final String words = wordsOf(failure);
        if (contains(words, DISK_FULL_WORDS)) {
            return Optional.of(FailureReason.DISK_FULL);
        }
        if (contains(words, READ_ONLY_WORDS)) {
            return Optional.of(FailureReason.READ_ONLY);
        }
        return switch (failure) {
            case NoSuchFileException _ -> Optional.of(FailureReason.FOLDER_MISSING);
            case AccessDeniedException _ -> Optional.of(FailureReason.ACCESS_DENIED);
            case FileSystemException _ when contains(words, DENIED_WORDS) -> Optional.of(FailureReason.ACCESS_DENIED);
            default -> Optional.empty();
        };
    }

    private static String wordsOf(final Throwable failure) {
        final String said = failure instanceof FileSystemException system ? system.getReason() : failure.getMessage();
        return said == null ? "" : said.toLowerCase(Locale.ROOT);
    }

    private static boolean contains(final String words, final List<String> any) {
        return any.stream().anyMatch(words::contains);
    }
}
