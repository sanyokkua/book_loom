package ua.bookloom.document.txt;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.detect.CharsetLadder;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.util.hash.HashUtil;

/**
 * Opens a plain-text file into the {@code :api} document model: the original byte buffer as the skeleton, plus one
 * {@code PARAGRAPH} segment per blank-line-separated paragraph.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TxtReader {

    static final String TXT_MEDIA_TYPE = "text/plain";

    private static final double INITIAL_CONFIDENCE = 0.0;

    private final OpenTxtRegistry registry;

    /**
     * Opens a plain-text file into the {@code :api} document model.
     *
     * @param source the {@code .txt} file to open
     * @return the parsed document
     * @throws CorruptContainerException if the file cannot be read
     */
    public Document read(Path source) {
        Objects.requireNonNull(source, "source");
        final byte[] fileBytes = readAllBytes(source);
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(fileBytes);
        final Charset charset = resolution.charset();
        final String sourceName = source.getFileName().toString();

        // Scanning starts past any byte-order mark, so the mark sits outside every span and is copied through on
        // export rather than being part of a paragraph a translation could replace.
        final List<ByteSpanAnchor> paragraphs = ParagraphScanner.scan(fileBytes, resolution.bomLength());
        final List<Segment> segments = segmentsOf(paragraphs, fileBytes, charset, sourceName);

        final String documentId = UUID.randomUUID().toString();
        registry.put(documentId, new OpenTxtRegistry.ParsedTxt(fileBytes, charset));
        return new Document(
                documentId,
                BookFormat.TXT,
                null,
                null,
                charset.name(),
                resolution.hasBom(),
                HashUtil.sha256Hex(fileBytes),
                Map.of(),
                List.of(unitOf(sourceName, segments)));
    }

    private static List<Segment> segmentsOf(
            List<ByteSpanAnchor> paragraphs, byte[] fileBytes, Charset charset, String unitId) {
        final List<Segment> segments = new ArrayList<>(paragraphs.size());
        for (int order = 0; order < paragraphs.size(); order++) {
            segments.add(toSegment(paragraphs, order, fileBytes, charset, unitId));
        }
        return segments;
    }

    private static Segment toSegment(
            List<ByteSpanAnchor> paragraphs, int order, byte[] fileBytes, Charset charset, String unitId) {
        final ByteSpanAnchor span = paragraphs.get(order);
        final String sourceInner = new String(fileBytes, span.startInclusive(), span.length(), charset);
        return new Segment(
                unitId + ":" + order,
                unitId,
                order,
                SegmentKind.PARAGRAPH,
                sourceInner,
                sourceInner,
                Map.of(),
                HashUtil.sha256OfNfcText(sourceInner),
                order > 0 ? unitId + ":" + (order - 1) : null,
                order < paragraphs.size() - 1 ? unitId + ":" + (order + 1) : null,
                span,
                null,
                SegmentStatus.PENDING,
                INITIAL_CONFIDENCE);
    }

    /**
     * Exactly one unit, whose id and href are both the source file name — for the same reason Markdown's needs
     * stating: the unit id seeds every segment id, and {@code segments.id} is a SQLite primary key.
     */
    private static Unit unitOf(String sourceName, List<Segment> segments) {
        return new Unit(
                sourceName,
                0,
                sourceName,
                TXT_MEDIA_TYPE,
                new SkeletonHandle(UUID.randomUUID().toString()),
                segments);
    }

    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new CorruptContainerException("Unable to read plain-text file", e);
        }
    }
}
