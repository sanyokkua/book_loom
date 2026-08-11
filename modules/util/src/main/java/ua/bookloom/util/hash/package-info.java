/**
 * SHA-256 hashing shared by every {@code :document} format importer — the whole-file document content hash
 * ({@code Document.contentHash}) and each segment's NFC-normalized pre-mask {@code sourceHash}
 * ({@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model}, FR-IMPORT-08).
 */
@NullMarked
package ua.bookloom.util.hash;

import org.jspecify.annotations.NullMarked;
