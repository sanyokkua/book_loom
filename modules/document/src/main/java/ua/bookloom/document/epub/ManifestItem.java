package ua.bookloom.document.epub;

import java.util.Objects;

/**
 * One OPF {@code <manifest><item>} — a resource the package declares, whether or not the spine references it.
 *
 * <p>Top-level rather than nested inside the parser because DRM adjudication reads the whole manifest: it has to
 * establish the media type of every encrypted resource, including fonts and images the spine never mentions
 * (ADR-0026).
 *
 * @param href the item's path as declared, relative to the OPF's own directory
 * @param mediaType the item's declared media type
 */
record ManifestItem(String href, String mediaType) {

    ManifestItem {
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(mediaType, "mediaType");
    }
}
