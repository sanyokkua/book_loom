package ua.bookloom.document.epub;

import org.jspecify.annotations.Nullable;

/**
 * What {@link DrmAdjudicator#probeEncryption} found: whether content — not only obfuscated fonts — is encrypted,
 * and the DRM scheme's name where it can be identified (task 4.2).
 *
 * @param contentEncrypted {@code true} when at least one encrypted resource is not a manifest-declared font
 * @param scheme the DRM scheme's name (for example {@code "Adobe ADEPT"}), or {@code null} when content is not
 *     encrypted, or when it is but no scheme could be identified
 */
record EncryptionFinding(boolean contentEncrypted, @Nullable String scheme) {

    static EncryptionFinding notEncrypted() {
        return new EncryptionFinding(false, null);
    }

    static EncryptionFinding encrypted(@Nullable String scheme) {
        return new EncryptionFinding(true, scheme);
    }
}
