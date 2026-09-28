package ua.bookloom.document.fb2;

import com.google.inject.Inject;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.detect.CharsetLadder;
import ua.bookloom.document.inspect.BodySegments;
import ua.bookloom.document.inspect.FormatInspection;
import ua.bookloom.document.inspect.FormattingClassifier;
import ua.bookloom.document.inspect.LanguageEvidenceReader;
import ua.bookloom.document.inspect.WordCounter;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SecureXml;
import ua.bookloom.document.model.ZipEncryption;
import ua.bookloom.document.model.ZipEntryReader;

/**
 * FictionBook 2's own {@link FormatInspection} (task 4.2): readable with its declared language, or DRM-protected
 * — named {@code ZIP encryption} — for a zipped source whose member declares the zip encryption flag.
 *
 * <p>The encryption flag is read directly off the raw bytes with {@link ZipEncryption#declaresEncryptedEntry},
 * <strong>never</strong> through {@code Fb2Reader#read}, whose {@code unpack} step throws for exactly this case:
 * an inspection's refusing verdict must be a normal answer (ADR-0039), not a caught exception.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class Fb2Inspection implements FormatInspection {

    private static final String FB2_TYPE = "FB2";
    private static final String ZIP_SUFFIX = ".fb2.zip";
    private static final String FB2_SUFFIX = ".fb2";
    private static final String ZIP_ENCRYPTION_SCHEME = "ZIP encryption";
    private static final String COVERPAGE_ELEMENT = "coverpage";
    private static final String IMAGE_ELEMENT = "image";
    private static final String BINARY_ELEMENT = "binary";
    private static final String HREF_LOCAL_NAME = "href";
    private static final String ID_ATTRIBUTE = "id";
    private static final String CONTENT_TYPE_ATTRIBUTE = "content-type";
    private static final String FRAGMENT_PREFIX = "#";
    private static final String DEFAULT_BINARY_MEDIA_TYPE = "application/octet-stream";
    private static final String IMAGE_MEDIA_PREFIX = "image/";
    private static final String NOTES_BODY_NAME = "notes";
    private static final String SECTION_ELEMENT = "section";
    private static final String BODY_ELEMENT_NAME = "body";
    private static final String NAME_ATTRIBUTE = "name";

    private final OpenFb2Registry registry;

    @Override
    public BookInspection inspect(Path source) {
        Objects.requireNonNull(source, "source");
        final byte[] fileBytes = readAllBytes(source);
        final boolean zip = isZip(source);
        if (zip && ZipEncryption.declaresEncryptedEntry(fileBytes)) {
            log.debug("FB2 zip member declares the encrypted flag source={}", source);
            return drmProtected();
        }
        try {
            return inspectReadable(fileBytes, zip);
        } catch (CorruptContainerException unreadable) {
            log.debug("FB2 unreadable for inspection source={} reason={}", source, unreadable.getMessage());
            return readableNoEvidence();
        }
    }

    private BookInspection inspectReadable(byte[] fileBytes, boolean zip) {
        final byte[] memberBytes = zip ? unzippedMember(fileBytes) : fileBytes;
        final CharsetLadder.Resolution resolution =
                CharsetLadder.resolve(memberBytes, Fb2Encoding.declaredEncodingName(memberBytes));
        final org.jdom2.Document tree = parse(memberBytes, resolution);
        final Element titleInfo =
                Fb2Metadata.childOf(Fb2Metadata.childOf(tree.getRootElement(), "description"), "title-info");
        final LanguageEvidence evidence =
                LanguageEvidenceReader.evaluate(Fb2Metadata.declaredLang(titleInfo), List.of());
        return new BookInspection(InspectionVerdict.READABLE, BookFormat.FB2, null, FB2_TYPE, null, evidence);
    }

    private static byte[] unzippedMember(byte[] fileBytes) {
        for (final RawEntry entry : ZipEntryReader.readAll(fileBytes)) {
            if (entry.name().toLowerCase(Locale.ROOT).endsWith(FB2_SUFFIX)) {
                return entry.content();
            }
        }
        throw new CorruptContainerException("The archive contains no FictionBook member");
    }

    private static org.jdom2.Document parse(byte[] fileBytes, CharsetLadder.Resolution resolution) {
        final Charset charset = resolution.charset();
        final String text =
                new String(fileBytes, resolution.bomLength(), fileBytes.length - resolution.bomLength(), charset);
        try {
            return SecureXml.builder().build(new CharArrayReader(text.toCharArray()));
        } catch (JDOMException | IOException e) {
            throw new CorruptContainerException("This file is not a well-formed FictionBook document", e);
        }
    }

    private static boolean isZip(Path source) {
        return source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX);
    }

    private static BookInspection drmProtected() {
        return new BookInspection(
                InspectionVerdict.DRM_PROTECTED,
                BookFormat.FB2,
                null,
                FB2_TYPE,
                ZIP_ENCRYPTION_SCHEME,
                LanguageEvidenceReader.evaluate(null, List.of()));
    }

    private static BookInspection readableNoEvidence() {
        return new BookInspection(
                InspectionVerdict.READABLE,
                BookFormat.FB2,
                null,
                FB2_TYPE,
                null,
                LanguageEvidenceReader.evaluate(null, List.of()));
    }

    /**
     * A genuine I/O fault reading the file propagates so {@code BookInspectorService}'s boundary turns it into a
     * failed {@code Result}, distinct from a malformed document's {@code READABLE}-with-no-evidence verdict.
     */
    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read FictionBook file for inspection", e);
        }
    }

    /**
     * Resolves {@code description/title-info/coverpage/image}'s {@code l:href} to the {@code binary} element it
     * names (task 4.3), read from the registry-held tree {@link Fb2Reader} parsed. Reports no cover, never a
     * failure, when the book declares no coverpage, or its reference names no binary this document actually
     * carries.
     */
    @Override
    public Optional<CoverImage> cover(Document document) {
        Objects.requireNonNull(document, "document");
        final Optional<ParsedFb2> parsed = registry.find(document.id());
        if (parsed.isEmpty()) {
            log.debug("No open FB2 state for cover lookup documentId={}", document.id());
            return Optional.empty();
        }
        return coverOf(parsed.get());
    }

    private Optional<CoverImage> coverOf(ParsedFb2 parsed) {
        final org.jdom2.Document tree = parsed.document();
        final Element titleInfo =
                Fb2Metadata.childOf(Fb2Metadata.childOf(tree.getRootElement(), "description"), "title-info");
        final Element image = Fb2Metadata.childOf(Fb2Metadata.childOf(titleInfo, COVERPAGE_ELEMENT), IMAGE_ELEMENT);
        final String href = image == null ? null : hrefOf(image);
        if (href == null || !href.startsWith(FRAGMENT_PREFIX)) {
            log.debug("FB2 declares no resolvable coverpage image reference");
            return Optional.empty();
        }
        final String binaryId = href.substring(FRAGMENT_PREFIX.length());
        final Optional<CoverImage> cover = binaryCoverOf(tree, binaryId);
        if (cover.isPresent()) {
            log.debug("FB2 cover matched coverpage image binaryId={}", binaryId);
        } else {
            log.debug("FB2 coverpage image names a binary this document does not carry binaryId={}", binaryId);
        }
        return cover;
    }

    private static Optional<CoverImage> binaryCoverOf(org.jdom2.Document tree, String binaryId) {
        for (final Element binary : tree.getRootElement().getChildren()) {
            if (BINARY_ELEMENT.equals(binary.getName()) && binaryId.equals(binary.getAttributeValue(ID_ATTRIBUTE))) {
                final String contentType = binary.getAttributeValue(CONTENT_TYPE_ATTRIBUTE);
                final byte[] bytes = Base64.getMimeDecoder().decode(binary.getText());
                return Optional.of(
                        new CoverImage(binaryId, contentType == null ? DEFAULT_BINARY_MEDIA_TYPE : contentType, bytes));
            }
        }
        return Optional.empty();
    }

    /**
     * Builds this FB2's structure tree from its own bodies (task 4.4), read from the registry-held tree
     * {@link Fb2Reader} parsed. Reports no nodes, never a failure, when no open state remains for this document.
     */
    @Override
    public List<StructureNode> structure(Document document) {
        Objects.requireNonNull(document, "document");
        final Optional<ParsedFb2> parsed = registry.find(document.id());
        if (parsed.isEmpty()) {
            log.debug("No open FB2 state for structure lookup documentId={}", document.id());
            return List.of();
        }
        return Fb2StructureBuilder.build(parsed.get(), document);
    }

    /**
     * The {@code image} element's link-target attribute, matched by local name so it reads regardless of which
     * prefix — {@code l} or {@code xlink} — the source declares for the XLink namespace (mirrors
     * {@code Jdom2TreeNode}'s own note on this).
     */
    private static @Nullable String hrefOf(Element element) {
        for (final Attribute attribute : element.getAttributes()) {
            if (HREF_LOCAL_NAME.equals(attribute.getName())) {
                return attribute.getValue();
            }
        }
        return null;
    }

    /**
     * Computes this FB2's statistics (task 4.5) from the registry-held tree {@link Fb2Reader} parsed.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public BookStats stats(Document document) {
        Objects.requireNonNull(document, "document");
        final ParsedFb2 parsed = requireOpen(document);
        final Element root = parsed.document().getRootElement();
        final List<Segment> bodySegments = BodySegments.of(document);
        final BookStats stats = new BookStats(
                bodySegments.size(),
                WordCounter.count(bodySegments, document.declaredLang()),
                countBinaries(root, media -> media.startsWith(IMAGE_MEDIA_PREFIX)),
                0,
                0,
                countByKind(bodySegments, SegmentKind.VERSE_LINE),
                countFootnotes(root),
                0,
                formattingOf(bodySegments));
        log.debug(
                "FB2 stats documentId={} segments={} words={} images={} footnotes={}",
                document.id(),
                stats.segments(),
                stats.words(),
                stats.images(),
                stats.footnotes());
        return stats;
    }

    private static int countByKind(List<Segment> segments, SegmentKind kind) {
        int count = 0;
        for (final Segment segment : segments) {
            if (segment.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    private static Set<Formatting> formattingOf(List<Segment> segments) {
        final Set<Formatting> formatting = EnumSet.noneOf(Formatting.class);
        for (final Segment segment : segments) {
            for (final String fragment : segment.placeholders().values()) {
                final Formatting kind = FormattingClassifier.classifyTag(fragment);
                if (kind != null) {
                    formatting.add(kind);
                }
            }
        }
        return formatting;
    }

    private static int countBinaries(Element root, Predicate<String> mediaTypeMatches) {
        int count = 0;
        for (final Element binary : root.getChildren()) {
            if (BINARY_ELEMENT.equals(binary.getName())) {
                final String contentType = binary.getAttributeValue(CONTENT_TYPE_ATTRIBUTE);
                if (contentType != null && mediaTypeMatches.test(contentType)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Every {@code section} anywhere inside the book's {@code body name="notes"}, each counted as one footnote. */
    private static int countFootnotes(Element root) {
        int count = 0;
        for (final Element child : root.getChildren()) {
            if (BODY_ELEMENT_NAME.equals(child.getName())
                    && NOTES_BODY_NAME.equals(child.getAttributeValue(NAME_ATTRIBUTE))) {
                count += countSections(child);
            }
        }
        return count;
    }

    private static int countSections(Element element) {
        int count = 0;
        for (final Element child : element.getChildren()) {
            if (SECTION_ELEMENT.equals(child.getName())) {
                count++;
            }
            count += countSections(child);
        }
        return count;
    }

    /**
     * Collects every resource id this FB2 carries (task 4.5): its binary ids, plus every id an {@code l:href}
     * fragment targets anywhere in the book.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public Set<String> resourceIds(Document document) {
        Objects.requireNonNull(document, "document");
        final ParsedFb2 parsed = requireOpen(document);
        final Element root = parsed.document().getRootElement();
        final Set<String> ids = new LinkedHashSet<>();
        for (final Element child : root.getChildren()) {
            if (BINARY_ELEMENT.equals(child.getName())) {
                final String id = child.getAttributeValue(ID_ATTRIBUTE);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        collectHrefFragmentTargets(root, ids);
        log.debug("FB2 resource ids documentId={} count={}", document.id(), ids.size());
        return ids;
    }

    private static void collectHrefFragmentTargets(Element element, Set<String> targets) {
        final String href = hrefOf(element);
        if (href != null && href.startsWith(FRAGMENT_PREFIX) && href.length() > FRAGMENT_PREFIX.length()) {
            targets.add(href.substring(FRAGMENT_PREFIX.length()));
        }
        for (final Element child : element.getChildren()) {
            collectHrefFragmentTargets(child, targets);
        }
    }

    private ParsedFb2 requireOpen(Document document) {
        return registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));
    }
}
