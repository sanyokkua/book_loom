package ua.bookloom.util.hash;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SHA-256 hashing for the two hash kinds {@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model} defines: the
 * document-scoped content hash and each segment's pre-mask {@code sourceHash}.
 *
 * <p>Centralized here, in {@code :util}, rather than reimplemented per format in {@code :document} — both hash
 * kinds must be computed identically wherever a format importer needs one (task 2.9, FR-IMPORT-08).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HashUtil {

    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_BYTES = 64 * 1024;

    /**
     * Hashes raw bytes — the shape {@code Document.contentHash} uses, computed once over the whole imported file.
     *
     * @param bytes the bytes to hash
     * @return the lowercase hex-encoded SHA-256 digest
     */
    public static String sha256Hex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return HexFormat.of().formatHex(digest(bytes));
    }

    /**
     * Hashes a file's bytes as {@link #sha256Hex(byte[])} does, reading it in small pieces so a file of any size is
     * hashed without holding it in memory.
     *
     * @param file the file to hash
     * @return the lowercase hex-encoded SHA-256 digest of the file's bytes
     * @throws UncheckedIOException when the file cannot be read; the cause is the original {@link IOException}
     */
    public static String sha256HexOfFile(final Path file) {
        Objects.requireNonNull(file, "file");
        final MessageDigest digest = newDigest();
        final byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(buffer);
            while (read >= 0) {
                digest.update(buffer, 0, read);
                read = in.read(buffer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the file could not be read for hashing", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Hashes a segment's pre-mask inner content for {@code Segment.sourceHash} — NFC-normalizing first, since two
     * source strings that differ only in Unicode normalization form must still hash identically
     * ({@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model}: "SHA-256 over the exact sourceInner, pre-mask,
     * NFC-normalized").
     *
     * @param sourceInner the block's pre-mask inner content
     * @return the lowercase hex-encoded SHA-256 digest of the NFC-normalized, UTF-8-encoded text
     */
    public static String sha256OfNfcText(String sourceInner) {
        Objects.requireNonNull(sourceInner, "sourceInner");
        final String normalized = Normalizer.normalize(sourceInner, Normalizer.Form.NFC);
        return sha256Hex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] bytes) {
        return newDigest().digest(bytes);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory algorithm every JDK's MessageDigest implementation must provide (see its
            // class javadoc), so this branch is unreachable on any conforming JVM — wrapping unchecked here
            // cannot discard a real, actionable failure.
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", e);
        }
    }
}
