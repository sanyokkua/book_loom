package ua.bookloom.document.epub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SecureXml;

/**
 * Locates the OPF package document by parsing {@code META-INF/container.xml} (task 2.3, FR-DOC-EPUB-1).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ContainerReader {

    private static final Namespace CONTAINER_NS =
            Namespace.getNamespace("urn:oasis:names:tc:opendocument:xmlns:container");

    /**
     * Locates the OPF's path by parsing {@code META-INF/container.xml}.
     *
     * @param containerEntry {@code META-INF/container.xml}'s raw entry, or {@code null} if the archive has none
     * @return the OPF's path within the archive
     * @throws CorruptContainerException if the entry is absent, malformed, or names no usable OPF path
     */
    static String locateOpf(@Nullable RawEntry containerEntry) {
        if (containerEntry == null) {
            throw new CorruptContainerException("Missing META-INF/container.xml");
        }
        final Element root = parse(containerEntry.content()).getRootElement();
        final Element rootfiles = root.getChild("rootfiles", CONTAINER_NS);
        final Element rootfile = rootfiles == null ? null : rootfiles.getChild("rootfile", CONTAINER_NS);
        final String fullPath = rootfile == null ? null : rootfile.getAttributeValue("full-path");
        if (fullPath == null || fullPath.isBlank()) {
            throw new CorruptContainerException("container.xml is missing a usable rootfile full-path");
        }
        return fullPath;
    }

    private static org.jdom2.Document parse(byte[] content) {
        try {
            return SecureXml.builder().build(new ByteArrayInputStream(content));
        } catch (JDOMException | IOException e) {
            throw new CorruptContainerException("Malformed META-INF/container.xml", e);
        }
    }
}
