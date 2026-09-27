package ua.bookloom.api.document;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What {@link BookInspector#inspect} found about a candidate file before it is opened, including — when the file
 * is refused — enough detail for the import screen to name why (ADR-0039).
 *
 * @param verdict whether the file can be opened, or the reason it is refused
 * @param format the detected format, or {@code null} when {@code verdict} is {@link InspectionVerdict#UNSUPPORTED}
 * @param formatVersion the detected format's version string when one is meaningful (an EPUB's package version, for
 *     example), or {@code null} when not applicable or not determined
 * @param detectedType a human-readable name for what the file actually is (for example {@code "PDF"} or
 *     {@code "EPUB"}), shown so a refusal names the file's real type rather than only saying it is unsupported
 * @param encryptionScheme the DRM scheme's name (for example {@code "Adobe ADEPT"}) when {@code verdict} is
 *     {@link InspectionVerdict#DRM_PROTECTED}, or {@code null} otherwise
 * @param languageEvidence what the file's own metadata says about its language, compared to its content
 */
public record BookInspection(
        InspectionVerdict verdict,
        @Nullable BookFormat format,
        @Nullable String formatVersion,
        String detectedType,
        @Nullable String encryptionScheme,
        LanguageEvidence languageEvidence) {

    /**
     * Validates the non-nullable components a caller is entitled to assume.
     */
    public BookInspection {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(detectedType, "detectedType");
        Objects.requireNonNull(languageEvidence, "languageEvidence");
    }
}
