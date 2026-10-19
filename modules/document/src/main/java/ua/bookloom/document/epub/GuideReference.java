package ua.bookloom.document.epub;

import java.util.Objects;

/**
 * One EPUB 2 {@code <guide><reference>} entry — the legacy way a package names its cover document, among other
 * roles (task 4.2, read by {@link EpubInspection}'s cover rules, task 4.3).
 *
 * @param type the reference's declared {@code type} (for example {@code "cover"})
 * @param href the referenced document's path, relative to the OPF's own directory
 */
record GuideReference(String type, String href) {

    GuideReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(href, "href");
    }
}
