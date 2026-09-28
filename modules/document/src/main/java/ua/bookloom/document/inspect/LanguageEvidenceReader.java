package ua.bookloom.document.inspect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.util.lang.LanguageTags;

/**
 * Computes what a book's metadata says about its language ({@code specs/document-round-trip/spec.md} "Report the
 * language evidence a book's metadata gives") from a declared raw code and, where the format has one, each
 * content document's own root declaration — never from reading a document's prose, which would make this a
 * language detector rather than a cheap read of what the book already declares.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LanguageEvidenceReader {

    /** A tag needs strictly more than half the declaring documents' votes to be the majority. */
    private static final int MAJORITY_DIVISOR = 2;

    /**
     * Evaluates a book's language evidence.
     *
     * @param declaredRaw the book's declared language exactly as written in its own metadata, or {@code null}/blank
     *     when it declares none
     * @param contentDeclarations each content document's own root-level declaration, one entry per document, or
     *     {@code null} for a document that declares none; empty for a format with no per-document declaration
     * @return the evidence: the normalized declaration, the content majority, and the verdict comparing them
     */
    public static LanguageEvidence evaluate(@Nullable String declaredRaw, List<@Nullable String> contentDeclarations) {
        Objects.requireNonNull(contentDeclarations, "contentDeclarations");
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final String majority = majorityOf(contentDeclarations, counts);
        final String normalizedDeclared = LanguageTags.normalize(declaredRaw).orElse(null);
        final LanguageEvidence.Verdict verdict = verdictOf(declaredRaw, normalizedDeclared, majority);
        log.debug(
                "language evidence: declaredRaw={} normalized={} counts={} majority={} verdict={}",
                declaredRaw,
                normalizedDeclared,
                counts,
                majority,
                verdict);
        return new LanguageEvidence(blankToNull(declaredRaw), normalizedDeclared, majority, verdict);
    }

    /**
     * The language declared by more than half of the documents that declare one, or {@code null} when there is no
     * such majority. {@code counts} is filled with each normalized tag's vote count for the caller's log line.
     */
    private static @Nullable String majorityOf(
            List<@Nullable String> contentDeclarations, Map<String, Integer> counts) {
        int declaring = 0;
        for (final String raw : contentDeclarations) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            declaring++;
            LanguageTags.normalize(raw).ifPresent(tag -> counts.merge(tag, 1, Integer::sum));
        }
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() * MAJORITY_DIVISOR > declaring) {
                return entry.getKey();
            }
        }
        log.debug("no content majority among {} declaring document(s)", declaring);
        return null;
    }

    private static LanguageEvidence.Verdict verdictOf(
            @Nullable String declaredRaw, @Nullable String normalizedDeclared, @Nullable String majority) {
        if (declaredRaw == null || declaredRaw.isBlank()) {
            log.debug("no declared language");
            return LanguageEvidence.Verdict.ABSENT;
        }
        if (normalizedDeclared == null) {
            log.debug("declared language '{}' is not recognized", declaredRaw);
            return LanguageEvidence.Verdict.UNRECOGNIZED;
        }
        if (majority == null || majority.equals(normalizedDeclared)) {
            return LanguageEvidence.Verdict.MATCH;
        }
        return LanguageEvidence.Verdict.MISMATCH;
    }

    private static @Nullable String blankToNull(@Nullable String raw) {
        return raw == null || raw.isBlank() ? null : raw;
    }
}
