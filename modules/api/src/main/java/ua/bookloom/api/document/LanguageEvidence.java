package ua.bookloom.api.document;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a book's own metadata says about its language, compared against what its content appears to be
 * ({@code specs/document-round-trip/spec.md} "Report the language evidence a book's metadata gives").
 *
 * @param declaredRaw the book's declared language exactly as written in its metadata, unnormalized, or {@code null}
 *     when it declares none
 * @param declared the declared language normalized to a recognizable tag, or {@code null} when absent or
 *     unrecognizable
 * @param contentMajority the language the book's content majority appears to be in, or {@code null} when not
 *     determined
 * @param verdict how the declared language compares to the content majority
 */
public record LanguageEvidence(
        @Nullable String declaredRaw,
        @Nullable String declared,
        @Nullable String contentMajority,
        Verdict verdict) {

    /**
     * Validates the non-nullable {@code verdict} component.
     */
    public LanguageEvidence {
        Objects.requireNonNull(verdict, "verdict");
    }

    /**
     * How a book's declared language compares to the language its content majority appears to be in.
     */
    public enum Verdict {

        /** The declared language matches the content majority. */
        MATCH,

        /** The declared language differs from the content majority. */
        MISMATCH,

        /** The declared language could not be resolved to a recognizable tag. */
        UNRECOGNIZED,

        /** The book declares no language. */
        ABSENT
    }
}
