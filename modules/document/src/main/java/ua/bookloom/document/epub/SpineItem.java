package ua.bookloom.document.epub;

import java.util.Objects;

/**
 * One resolved spine entry — a manifest item the spine references, in declared spine order (FR-DOC-EPUB-1).
 *
 * @param idref the manifest item id this spine entry references
 * @param href the referenced manifest item's {@code href}, relative to the OPF
 * @param mediaType the referenced manifest item's declared media type
 */
record SpineItem(String idref, String href, String mediaType) {

    SpineItem {
        Objects.requireNonNull(idref, "idref");
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(mediaType, "mediaType");
    }
}
