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
 * @param version the package element's own declared {@code version} (for example {@code "3.0"}), or {@code null}
 *     when the package declares none — read for {@link EpubInspection}'s format-version answer (task 4.2)
 * @param coverMetaContent the EPUB 2 {@code <meta name="cover" content="…">} element's {@code content} value, or
 *     {@code null} when the package declares none — read for {@link EpubInspection}'s cover rules (task 4.3)
 * @param guideReferences every EPUB 2 {@code <guide><reference>} entry, in document order; empty if the package
 *     declares no guide — read for {@link EpubInspection}'s cover rules (task 4.3)
 * @param spineToc the spine element's own {@code toc} attribute — the manifest id of the EPUB 2 NCX — or
 *     {@code null} when the spine declares none — read for {@link EpubInspection}'s structure rules (task 4.4)
 */
record ParsedOpf(
        String opfPath,
        List<String> dcLanguages,
        @Nullable String title,
        @Nullable String author,
        List<ManifestItem> manifestItems,
        List<SpineItem> spineItems,
        Document jdomDocument,
        @Nullable String version,
        @Nullable String coverMetaContent,
        List<GuideReference> guideReferences,
        @Nullable String spineToc) {

    ParsedOpf {
        Objects.requireNonNull(opfPath, "opfPath");
        Objects.requireNonNull(dcLanguages, "dcLanguages");
        Objects.requireNonNull(manifestItems, "manifestItems");
        Objects.requireNonNull(spineItems, "spineItems");
        Objects.requireNonNull(jdomDocument, "jdomDocument");
        Objects.requireNonNull(guideReferences, "guideReferences");
        dcLanguages = List.copyOf(dcLanguages);
        manifestItems = List.copyOf(manifestItems);
        spineItems = List.copyOf(spineItems);
        guideReferences = List.copyOf(guideReferences);
    }
}
