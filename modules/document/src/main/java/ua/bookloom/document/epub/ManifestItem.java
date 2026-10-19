package ua.bookloom.document.epub;

import java.util.Objects;
import java.util.Set;

/**
 * One OPF {@code <manifest><item>} — a resource the package declares, whether or not the spine references it.
 *
 * <p>Top-level rather than nested inside the parser because DRM adjudication reads the whole manifest: it has to
 * establish the media type of every encrypted resource, including fonts and images the spine never mentions
 * (ADR-0026). The manifest id and its {@code properties} were added in task 4.2 — the EPUB 3 cover-image rule and
 * the navigation-document rule both key off a manifest item's own id and its {@code properties} attribute, which
 * {@code href}/{@code mediaType} alone cannot answer.
 *
 * @param id the item's own manifest id
 * @param href the item's path as declared, relative to the OPF's own directory
 * @param mediaType the item's declared media type
 * @param properties the item's declared {@code properties} attribute, split on whitespace; empty if the item
 *     declares none
 */
record ManifestItem(String id, String href, String mediaType, Set<String> properties) {

    ManifestItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(properties, "properties");
        properties = Set.copyOf(properties);
    }
}
