package ua.bookloom.document.epub;

import java.util.List;
import java.util.Objects;
import org.jdom2.Document;
import org.jspecify.annotations.Nullable;

/**
 * The OPF's manifest, spine, and the metadata this change reads (task 2.5).
 *
 * @param opfPath the OPF's own path within the archive, as located by {@link ContainerReader}
 * @param dcLanguages every declared {@code dc:language}, in document order; empty if none
 * @param title the first declared {@code dc:title}, or {@code null} if none
 * @param author the first declared {@code dc:creator}, or {@code null} if none
 * @param manifestItems every declared manifest item, in document order — the whole manifest rather than only
 *     the spine, because DRM adjudication has to establish the media type of encrypted fonts and images the
 *     spine never references (ADR-0026)
 * @param spineItems the spine's items, resolved against the manifest, in declared spine order
 * @param jdomDocument the OPF's own parsed tree — kept so a later change can replace {@code dc:language} in it
 *     on export without re-parsing (design.md D1's "the tree itself never leaves :document", applied to the OPF)
 */
record ParsedOpf(
        String opfPath,
        List<String> dcLanguages,
        @Nullable String title,
        @Nullable String author,
        List<ManifestItem> manifestItems,
        List<SpineItem> spineItems,
        Document jdomDocument) {

    ParsedOpf {
        Objects.requireNonNull(opfPath, "opfPath");
        Objects.requireNonNull(dcLanguages, "dcLanguages");
        Objects.requireNonNull(manifestItems, "manifestItems");
        Objects.requireNonNull(spineItems, "spineItems");
        Objects.requireNonNull(jdomDocument, "jdomDocument");
        dcLanguages = List.copyOf(dcLanguages);
        manifestItems = List.copyOf(manifestItems);
        spineItems = List.copyOf(spineItems);
    }
}
