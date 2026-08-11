package ua.bookloom.document.fb2;

import com.google.inject.Inject;
import java.io.CharArrayReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.detect.CharsetLadder;
import ua.bookloom.document.model.BlockSegmentWalker;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DrmRefusedException;
import ua.bookloom.document.model.Jdom2TreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SecureXml;
import ua.bookloom.document.model.ZipEncryption;
import ua.bookloom.document.model.ZipEntryReader;
import ua.bookloom.util.hash.HashUtil;

/**
 * Opens a FictionBook 2 file — bare {@code .fb2} or zipped {@code .fb2.zip} — into the {@code :api} document
 * model.
 *
 * <p>The whole book is one XML document, so it is parsed once through the same strict JDOM2 configuration the OPF
 * uses, with external DTD and entity loading disabled. Comments, CDATA sections, entity spelling, namespace
 * prefixes and the whitespace between block elements all survive because nothing here rebuilds the tree — the
 * reader only reads positions out of it.
 *
 * <p>Throws rather than returning a {@code Result}, matching the EPUB reader: {@code DocumentService} is the port
 * boundary that classifies these into the typed envelope.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class Fb2Reader {

    /** FB2 has no registered media type; this is the value the format's own tooling uses. */
    static final String FB2_MEDIA_TYPE = "application/x-fictionbook+xml";

    private static final String ZIP_SUFFIX = ".fb2.zip";
    private static final String FB2_SUFFIX = ".fb2";
    private static final String BODY_ELEMENT = "body";

    private final OpenFb2Registry registry;

    /**
     * Opens an FB2 book into the {@code :api} document model.
     *
     * @param source the {@code .fb2} or {@code .fb2.zip} file to open
     * @return the parsed document
     * @throws CorruptContainerException if the file is not well-formed FB2, if a zipped source contains no FB2
     *     member, or if the declared encoding contradicts the content
     * @throws DrmRefusedException if a zipped source declares an encrypted entry
     */
    public Document read(Path source) {
        Objects.requireNonNull(source, "source");
        final Source unpacked = unpack(source);
        return readDocument(unpacked);
    }

    /**
     * Unwraps a zip container if there is one. The encryption flag is checked before anything is inflated,
     * because an encrypted member must be reported as a protected book rather than as a corrupt archive.
     */
    private static Source unpack(Path source) {
        final byte[] fileBytes = readAllBytes(source);
        final String fileName = source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX)) {
            return new Source(fileBytes, fileName, null);
        }
        if (ZipEncryption.declaresEncryptedEntry(fileBytes)) {
            throw new DrmRefusedException("The zipped FictionBook declares an encrypted entry");
        }
        final RawEntry member = requireFb2Member(ZipEntryReader.readAll(fileBytes));
        return new Source(member.content(), member.name(), member.name());
    }

    private static RawEntry requireFb2Member(List<RawEntry> entries) {
        for (final RawEntry entry : entries) {
            if (entry.name().toLowerCase(Locale.ROOT).endsWith(FB2_SUFFIX)) {
                return entry;
            }
        }
        throw new CorruptContainerException("The archive contains no FictionBook member");
    }

    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new CorruptContainerException("Unable to read FictionBook file", e);
        }
    }

    private Document readDocument(Source source) {
        final String declaredEncodingName = Fb2Encoding.declaredEncodingName(source.bytes());
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(source.bytes(), declaredEncodingName);
        final org.jdom2.Document tree = parse(source.bytes(), resolution);
        if (declaredEncodingName != null) {
            // After parsing, because the check reads the document's prose rather than its markup — but still
            // before any Document, Unit or Segment exists, so a refusal is a refusal and not a partial import.
            Fb2Encoding.refuseIfDeclarationContradictsContent(tree, resolution.charset());
        }
        return buildDocument(source, tree, resolution, declaredEncodingName);
    }

    private Document buildDocument(
            Source source,
            org.jdom2.Document tree,
            CharsetLadder.Resolution resolution,
            @Nullable String declaredEncodingName) {
        final Element titleInfo =
                Fb2Metadata.childOf(Fb2Metadata.childOf(tree.getRootElement(), "description"), "title-info");
        final UnitsResult units = readUnits(tree, source.name());
        final String documentId = UUID.randomUUID().toString();
        registry.put(
                documentId,
                new ParsedFb2(
                        tree,
                        source.name(),
                        source.zipMemberName(),
                        resolution.charset(),
                        declaredEncodingName,
                        units.bodiesByHandleId()));
        return new Document(
                documentId,
                BookFormat.FB2,
                Fb2Metadata.declaredLang(titleInfo),
                null,
                resolution.charset().name(),
                resolution.hasBom(),
                HashUtil.sha256Hex(source.bytes()),
                Fb2Metadata.read(titleInfo),
                units.units());
    }

    /**
     * Parses the decoded characters rather than the raw stream, so the charset the ladder resolved is the one
     * used — the parser must not re-decide it from a declaration the ladder has already adjudicated. Any
     * byte-order mark is dropped first, because an XML parser rejects one that reaches it as content.
     */
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

    /**
     * One unit per {@code <body>} in document order. The unit id carries the body's position because a book's
     * bodies all come from one file and would otherwise share one identity — and a unit id seeds every segment
     * id, which is a SQLite primary key.
     */
    private static UnitsResult readUnits(org.jdom2.Document tree, String sourceName) {
        final List<Unit> units = new ArrayList<>();
        final Map<String, Element> bodies = new LinkedHashMap<>();
        int order = 0;
        for (final Element body : tree.getRootElement().getChildren()) {
            if (BODY_ELEMENT.equals(body.getName())) {
                addUnit(units, bodies, body, sourceName, order);
                order++;
            }
        }
        if (units.isEmpty()) {
            throw new CorruptContainerException("This FictionBook document declares no body");
        }
        return new UnitsResult(units, bodies);
    }

    private static void addUnit(
            List<Unit> units, Map<String, Element> bodies, Element body, String sourceName, int order) {
        final String unitId = sourceName + "#" + order;
        final String handleId = UUID.randomUUID().toString();
        bodies.put(handleId, body);
        final List<Segment> segments = BlockSegmentWalker.walk(Jdom2TreeNode.of(body), unitId);
        units.add(new Unit(unitId, order, sourceName, FB2_MEDIA_TYPE, new SkeletonHandle(handleId), segments));
    }

    /** The FB2 bytes plus where they came from — identical for a bare file and for a zipped member. */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw file content, never compared as a value.
    private record Source(
            byte[] bytes,
            String name,
            @org.jspecify.annotations.Nullable String zipMemberName) {}

    private record UnitsResult(List<Unit> units, Map<String, Element> bodiesByHandleId) {}
}
