package ua.bookloom.api.document;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A parsed book: its identity, format, language information, a document-scoped content hash, its metadata, and its
 * ordered content units ({@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model}).
 *
 * @param id the document's stable id
 * @param format the format this document was parsed from and will be exported back to (ADR-0004, DD-30 — export
 *     is same-format-only)
 * @param declaredLang the source language the book itself declares, or {@code null} when it declares none
 * @param detectedSourceLang the source language detected from content, or {@code null}; present in the shape but
 *     never populated by this change — {@code add-metadata-units-and-language-detection} owns detection
 * @param charset the character encoding this document was read with and is written back in, or {@code null} for a
 *     container format that has no document-level encoding — an EPUB's spine documents each declare their own, so
 *     the container as a whole has no answer to give
 *     ({@code 01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom})
 * @param hasBom whether the source began with a byte-order mark, so export re-emits it exactly as found, or
 *     {@code null} where the question has no referent — recorded separately from {@code charset} because
 *     re-emitting a file that had no mark with one added is itself a change
 * @param contentHash the SHA-256 hash over the imported source file, used for resume/change-detection
 *     ({@code FR-IMPORT-08})
 * @param metadata book-level metadata such as title/author, defensively copied and unmodifiable
 * @param units this document's content units, in reading order, defensively copied and unmodifiable
 */
public record Document(
        String id,
        BookFormat format,
        @Nullable String declaredLang,
        @Nullable String detectedSourceLang,
        @Nullable String charset,
        @Nullable Boolean hasBom,
        String contentHash,
        Map<String, String> metadata,
        List<Unit> units) {

    /**
     * Validates the non-nullable components and defensively copies {@code metadata}/{@code units} so a
     * caller-held mutable collection cannot corrupt this record after construction.
     */
    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(units, "units");
        metadata = Map.copyOf(metadata);
        units = List.copyOf(units);
    }
}
