package ua.bookloom.document.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reads a zip container's central directory to answer one question: does any entry declare itself encrypted?
 *
 * <p><strong>Why this is not left to the inflater.</strong> {@link java.util.zip.ZipInputStream} refuses an
 * encrypted entry with a generic {@code ZipException}, which is indistinguishable from a truncated or corrupt
 * archive — so a password-protected book would be reported as malformed. EC-DRM-1 requires the opposite: a
 * message that identifies the book as <em>protected</em>, because that is the only part of the envelope that
 * tells a user which of the two happened. Reading the flag directly is the only way to know before inflating.
 *
 * <p>The central directory is read rather than the local file headers, because a local header's signature can
 * occur by chance inside compressed data while the directory is an authoritative index.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZipEncryption {

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_ENTRY_SIGNATURE = 0x02014b50;
    private static final int EOCD_MINIMUM_LENGTH = 22;

    /** The end-of-central-directory record may be followed by up to 64 KiB of comment. */
    private static final int MAX_EOCD_SEARCH_BYTES = 0xFFFF + EOCD_MINIMUM_LENGTH;

    private static final int EOCD_ENTRY_COUNT_OFFSET = 10;
    private static final int EOCD_DIRECTORY_OFFSET_OFFSET = 16;

    private static final int CENTRAL_ENTRY_FLAGS_OFFSET = 8;
    private static final int CENTRAL_ENTRY_FIXED_LENGTH = 46;
    private static final int CENTRAL_ENTRY_NAME_LENGTH_OFFSET = 28;
    private static final int CENTRAL_ENTRY_EXTRA_LENGTH_OFFSET = 30;
    private static final int CENTRAL_ENTRY_COMMENT_LENGTH_OFFSET = 32;

    /** Bit 0 of the general-purpose flag: "the file is encrypted". */
    private static final int ENCRYPTED_FLAG = 0x0001;

    /**
     * Whether any entry in the container declares itself encrypted.
     *
     * @param fileBytes the whole archive's bytes
     * @return {@code true} if at least one central-directory entry sets the encrypted flag; {@code false} when no
     *     entry does <em>and</em> when the directory cannot be read at all — an unreadable directory is a
     *     corrupt-container question, answered by the reader, not an encryption one
     */
    public static boolean declaresEncryptedEntry(byte[] fileBytes) {
        final int eocd = findEndOfCentralDirectory(fileBytes);
        if (eocd < 0) {
            return false;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int entryCount = Short.toUnsignedInt(buffer.getShort(eocd + EOCD_ENTRY_COUNT_OFFSET));
        return anyEntryEncrypted(
                buffer, fileBytes.length, buffer.getInt(eocd + EOCD_DIRECTORY_OFFSET_OFFSET), entryCount);
    }

    private static boolean anyEntryEncrypted(ByteBuffer buffer, int length, int directoryStart, int entryCount) {
        int cursor = directoryStart;
        for (int i = 0; i < entryCount; i++) {
            if (cursor < 0
                    || cursor + CENTRAL_ENTRY_FIXED_LENGTH > length
                    || buffer.getInt(cursor) != CENTRAL_ENTRY_SIGNATURE) {
                return false;
            }
            if ((Short.toUnsignedInt(buffer.getShort(cursor + CENTRAL_ENTRY_FLAGS_OFFSET)) & ENCRYPTED_FLAG) != 0) {
                return true;
            }
            cursor += nextEntryOffset(buffer, cursor);
        }
        return false;
    }

    private static int nextEntryOffset(ByteBuffer buffer, int cursor) {
        return CENTRAL_ENTRY_FIXED_LENGTH
                + Short.toUnsignedInt(buffer.getShort(cursor + CENTRAL_ENTRY_NAME_LENGTH_OFFSET))
                + Short.toUnsignedInt(buffer.getShort(cursor + CENTRAL_ENTRY_EXTRA_LENGTH_OFFSET))
                + Short.toUnsignedInt(buffer.getShort(cursor + CENTRAL_ENTRY_COMMENT_LENGTH_OFFSET));
    }

    /** Scans backwards from the end, because the record sits last and may carry a trailing comment. */
    private static int findEndOfCentralDirectory(byte[] fileBytes) {
        if (fileBytes.length < EOCD_MINIMUM_LENGTH) {
            return -1;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int earliest = Math.max(0, fileBytes.length - MAX_EOCD_SEARCH_BYTES);
        for (int at = fileBytes.length - EOCD_MINIMUM_LENGTH; at >= earliest; at--) {
            if (buffer.getInt(at) == EOCD_SIGNATURE) {
                return at;
            }
        }
        return -1;
    }
}
