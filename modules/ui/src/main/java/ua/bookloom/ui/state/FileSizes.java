package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Words the size of a written book in the largest unit that keeps it above one, through the catalogue. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileSizes {

    private static final long KILOBYTE = 1024;
    private static final long MEGABYTE = KILOBYTE * KILOBYTE;

    /**
     * Words a size as {@code 512 B}, {@code 936 KB} or {@code 1.5 MB}, with the decimal separator and unit of the
     * catalogue's language.
     *
     * @param bytes the size; never negative
     * @param messages the catalogue the unit and the number pattern come from
     * @return the wording; never empty
     */
    public static String format(final long bytes, final Messages messages) {
        Objects.requireNonNull(messages, "messages");
        if (bytes < KILOBYTE) {
            return messages.get(MessageKey.EXPORT_SIZE_BYTES, bytes);
        }
        if (bytes < MEGABYTE) {
            return messages.get(MessageKey.EXPORT_SIZE_KB, (double) bytes / KILOBYTE);
        }
        return messages.get(MessageKey.EXPORT_SIZE_MB, (double) bytes / MEGABYTE);
    }
}
