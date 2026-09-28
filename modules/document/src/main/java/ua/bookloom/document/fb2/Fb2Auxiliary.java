package ua.bookloom.document.fb2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Text;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.document.mask.MaskWriter;
import ua.bookloom.document.mask.MaskedContent;
import ua.bookloom.document.model.AuxiliarySlots;
import ua.bookloom.document.model.ElementPaths;
import ua.bookloom.document.model.Jdom2TreeNode;
import ua.bookloom.document.model.TreeDialect;
import ua.bookloom.document.model.TreeNode;

/**
 * Finds an FB2 book's auxiliary text slots (design.md D12): {@code title-info}'s {@code book-title}, each
 * {@code author} and each {@code annotation} paragraph. {@code src-title-info} and {@code document-info/history}
 * describe another book and another file, and an author's {@code id}, {@code email} and {@code home-page} are
 * identifiers and addresses, so none of them is read.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Fb2Auxiliary {

    private static final Set<String> NAME_PARTS = Set.of("first-name", "middle-name", "last-name", "nickname");

    /**
     * Collects the slots in the order title, authors, annotation paragraphs.
     *
     * @param root the book's root element
     * @param sourceName the file name, which names the resource every slot lives in
     * @return the auxiliary segments and the table that writes them back
     */
    static AuxiliarySlots.Collected collect(Element root, String sourceName) {
        final AuxiliarySlots.Builder builder = AuxiliarySlots.builder();
        final Element titleInfo = Fb2Metadata.childOf(Fb2Metadata.childOf(root, "description"), "title-info");
        if (titleInfo != null) {
            final TreeNode rootNode = Jdom2TreeNode.of(root);
            addTitle(builder, sourceName, root, rootNode, titleInfo);
            addAuthors(builder, sourceName, root, rootNode, titleInfo);
            addAnnotation(builder, sourceName, root, rootNode, titleInfo);
        }
        return builder.build();
    }

    private static void addTitle(
            AuxiliarySlots.Builder builder, String resource, Element root, TreeNode rootNode, Element titleInfo) {
        final Element title = Fb2Metadata.childOf(titleInfo, "book-title");
        if (title != null) {
            builder.addText(
                    resource,
                    "aux:title",
                    SegmentKind.METADATA_TITLE,
                    rootNode,
                    ElementPaths.below(root, title),
                    TreeDialect.FICTION_BOOK);
        }
    }

    private static void addAuthors(
            AuxiliarySlots.Builder builder, String resource, Element root, TreeNode rootNode, Element titleInfo) {
        int index = 0;
        for (final Element author : titleInfo.getChildren()) {
            if (!"author".equals(author.getName())) {
                continue;
            }
            final List<Part> parts = partsOf(author);
            if (parts.isEmpty()) {
                log.debug("author {} has no name parts; not produced", index);
                continue;
            }
            log.debug(
                    "author {} name parts read: {}",
                    index,
                    parts.stream().map(Part::name).toList());
            builder.addParts(
                    resource,
                    "aux:creator:" + index++,
                    SegmentKind.METADATA_AUTHOR,
                    rootNode,
                    ElementPaths.below(root, author),
                    sourceOf(parts),
                    maskOf(parts));
        }
    }

    /** The author's non-blank name parts in document order; only whether whitespace separated two is kept. */
    private static List<Part> partsOf(Element author) {
        final List<Part> parts = new ArrayList<>();
        boolean gap = false;
        for (final Content content : author.getContent()) {
            if (content instanceof Element part && NAME_PARTS.contains(part.getName())) {
                if (!part.getText().isBlank()) {
                    parts.add(new Part(part.getName(), part.getText(), gap && !parts.isEmpty()));
                    gap = false;
                }
            } else if (content instanceof Text text && text.getText().isBlank()) {
                gap = !text.getText().isEmpty();
            }
        }
        return parts;
    }

    /** Each name part is one pair around its text. */
    private static MaskedContent maskOf(List<Part> parts) {
        final MaskWriter writer = new MaskWriter();
        for (final Part part : parts) {
            if (part.spaceBefore()) {
                writer.appendCharacterData(" ");
            }
            writer.appendPairOpen("<" + part.name() + ">", null);
            writer.appendCharacterData(part.text());
            writer.appendPairClose("</" + part.name() + ">");
        }
        return writer.build();
    }

    private static String sourceOf(List<Part> parts) {
        final StringBuilder source = new StringBuilder();
        for (final Part part : parts) {
            source.append(part.spaceBefore() ? " " : "")
                    .append('<')
                    .append(part.name())
                    .append('>')
                    .append(part.text())
                    .append("</")
                    .append(part.name())
                    .append('>');
        }
        return source.toString();
    }

    private record Part(String name, String text, boolean spaceBefore) {}

    private static void addAnnotation(
            AuxiliarySlots.Builder builder, String resource, Element root, TreeNode rootNode, Element titleInfo) {
        final Element annotation = Fb2Metadata.childOf(titleInfo, "annotation");
        if (annotation == null) {
            return;
        }
        int index = 0;
        for (final Element paragraph : annotation.getChildren()) {
            if ("p".equals(paragraph.getName())) {
                builder.addMarkup(
                        resource,
                        "aux:description:" + index++,
                        SegmentKind.METADATA_DESCRIPTION,
                        rootNode,
                        ElementPaths.below(root, paragraph),
                        TreeDialect.FICTION_BOOK);
            }
        }
    }
}
