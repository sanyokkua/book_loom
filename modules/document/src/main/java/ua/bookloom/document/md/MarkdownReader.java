package ua.bookloom.document.md;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.detect.CharsetLadder;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.util.hash.HashUtil;

/**
 * Opens a Markdown file into the {@code :api} document model.
 *
 * <p>The skeleton is the original byte buffer, not a tree. The CommonMark parse is analysis only — it exists to
 * locate each leaf block's byte span, and its renderer is never invoked — because Markdown has no canonical
 * spelling: {@code *em*} and {@code _em_} are the same document, so re-rendering would normalise formatting
 * everywhere, in parts of the file nobody translated.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class MarkdownReader {

    static final String MARKDOWN_MEDIA_TYPE = "text/markdown";

    private final OpenMarkdownRegistry registry;

    /**
     * The parser, built per read. {@link Parser} is documented as thread-safe and could be shared, but building it
     * costs nothing next to reading a book and a per-call instance removes the question entirely.
     */
    private static Parser parser() {
        return Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build();
    }

    /**
     * Opens a Markdown file into the {@code :api} document model.
     *
     * @param source the {@code .md} file to open
     * @return the parsed document
     * @throws CorruptContainerException if the file cannot be read
     */
    public Document read(Path source) {
        Objects.requireNonNull(source, "source");
        final byte[] fileBytes = readAllBytes(source);
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(fileBytes);
        final Charset charset = resolution.charset();
        final String text =
                new String(fileBytes, resolution.bomLength(), fileBytes.length - resolution.bomLength(), charset);

        final Frontmatter.Split split = Frontmatter.split(text);
        final Map<String, String> metadata = Frontmatter.scan(split.block());
        // Source spans index the body, so the offset map is built over the body and the two things in front of it
        // — the byte-order mark and the frontmatter block — are added as a fixed prefix.
        final int bodyByteOffset = resolution.bomLength() + split.block().getBytes(charset).length;
        final int[] byteOffsets = MarkdownSpans.byteOffsets(split.body(), charset);

        final String sourceName = source.getFileName().toString();
        final Node parsed = parser().parse(split.body());
        final List<Segment> segments =
                MarkdownWalker.walk(parsed, split.body(), bodyByteOffset, byteOffsets, sourceName);

        final String documentId = UUID.randomUUID().toString();
        registry.put(documentId, new ParsedMarkdown(fileBytes, charset));
        return new Document(
                documentId,
                BookFormat.MARKDOWN,
                Frontmatter.declaredLang(metadata),
                null,
                charset.name(),
                resolution.hasBom(),
                HashUtil.sha256Hex(fileBytes),
                metadata,
                List.of(unitOf(sourceName, segments)));
    }

    /**
     * Exactly one unit, whose id and href are both the source file name. Single-unit formats need their identity
     * stated as deliberately as FB2's multi-body case does: the unit id seeds every segment id, and
     * {@code segments.id} is a SQLite primary key.
     */
    private static Unit unitOf(String sourceName, List<Segment> segments) {
        return new Unit(
                sourceName,
                0,
                sourceName,
                MARKDOWN_MEDIA_TYPE,
                new SkeletonHandle(UUID.randomUUID().toString()),
                segments);
    }

    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new CorruptContainerException("Unable to read Markdown file", e);
        }
    }
}
