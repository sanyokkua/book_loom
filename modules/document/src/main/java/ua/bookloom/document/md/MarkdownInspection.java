package ua.bookloom.document.md;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.detect.CharsetLadder;
import ua.bookloom.document.inspect.BodySegments;
import ua.bookloom.document.inspect.FormatInspection;
import ua.bookloom.document.inspect.FormattingClassifier;
import ua.bookloom.document.inspect.LanguageEvidenceReader;
import ua.bookloom.document.inspect.WordCounter;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * Markdown's own {@link FormatInspection} (task 4.2): always readable, since any bytes are valid Markdown content
 * — there is no corrupt-container question for this format, only its declared frontmatter language, if any.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class MarkdownInspection implements FormatInspection {

    private static final String MARKDOWN_TYPE = "Markdown";

    private final OpenMarkdownRegistry registry;

    @Override
    public BookInspection inspect(Path source) {
        Objects.requireNonNull(source, "source");
        final byte[] fileBytes = readAllBytes(source);
        final CharsetLadder.Resolution resolution = CharsetLadder.resolve(fileBytes);
        final Charset charset = resolution.charset();
        final String text =
                new String(fileBytes, resolution.bomLength(), fileBytes.length - resolution.bomLength(), charset);
        final Frontmatter.Split split = Frontmatter.split(text);
        final Map<String, String> metadata = Frontmatter.scan(split.block());
        final String declaredRaw = Frontmatter.declaredLang(metadata);
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate(declaredRaw, List.of());
        log.debug("Markdown frontmatter language source={} declaredRaw={}", source, declaredRaw);
        return new BookInspection(InspectionVerdict.READABLE, BookFormat.MARKDOWN, null, MARKDOWN_TYPE, null, evidence);
    }

    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read Markdown file for inspection", e);
        }
    }

    /**
     * Markdown carries no notion of a cover image (task 4.3): there is no rule to apply, so this always reports
     * none rather than guessing at, say, the first image the document happens to contain.
     */
    @Override
    public Optional<CoverImage> cover(Document document) {
        Objects.requireNonNull(document, "document");
        log.debug("Markdown declares no cover rule documentId={}", document.id());
        return Optional.empty();
    }

    /**
     * Builds this Markdown's structure tree from its own headings (task 4.4), nested by level: text before the
     * first heading is an untitled leading node, and each heading's own node counts every segment from itself to
     * the next heading of the same or a shallower level — its nested headings included.
     */
    @Override
    public List<StructureNode> structure(Document document) {
        Objects.requireNonNull(document, "document");
        final Optional<ParsedMarkdown> parsed = registry.find(document.id());
        if (parsed.isEmpty()) {
            log.debug("No open Markdown state for structure lookup documentId={}", document.id());
            return List.of();
        }
        return structureOf(parsed.get(), document);
    }

    private static List<StructureNode> structureOf(ParsedMarkdown parsed, Document document) {
        final String text = new String(parsed.originalBytes(), parsed.charset());
        final Frontmatter.Split split = Frontmatter.split(text);
        final Node root = MarkdownReader.parser().parse(split.body());
        final List<Integer> headingLevels = headingLevelsOf(root);
        final List<Segment> segments = document.units().get(0).segments();
        return MarkdownStructure.build(segments, headingLevels);
    }

    private static List<Integer> headingLevelsOf(Node root) {
        final List<Integer> levels = new ArrayList<>();
        collectHeadingLevels(root, levels);
        return levels;
    }

    private static void collectHeadingLevels(Node node, List<Integer> levels) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
                levels.add(heading.getLevel());
            }
            collectHeadingLevels(child, levels);
        }
    }

    /**
     * Computes this Markdown's statistics (task 4.5) from the registry-held bytes {@link MarkdownReader} parsed,
     * re-parsed the same way {@link #structure(Document)} does.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public BookStats stats(Document document) {
        Objects.requireNonNull(document, "document");
        final ParsedMarkdown parsed = requireOpen(document);
        final Node root = astOf(parsed);
        final List<Segment> bodySegments = BodySegments.of(document);
        final Counts counts = new Counts();
        collectCounts(root, counts);
        final BookStats stats = new BookStats(
                bodySegments.size(),
                WordCounter.count(bodySegments, document.declaredLang()),
                counts.images,
                counts.codeBlocks,
                0,
                0,
                0,
                counts.tables,
                formattingOf(bodySegments));
        log.debug(
                "Markdown stats documentId={} segments={} words={} images={} codeBlocks={} tables={}",
                document.id(),
                stats.segments(),
                stats.words(),
                stats.images(),
                stats.codeBlocks(),
                stats.tables());
        return stats;
    }

    private static final class Counts {
        private int images;
        private int codeBlocks;
        private int tables;
    }

    private static void collectCounts(Node node, Counts counts) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            tallyNode(child, counts);
            collectCounts(child, counts);
        }
    }

    private static void tallyNode(Node node, Counts counts) {
        if (node instanceof Image) {
            counts.images++;
        } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
            counts.codeBlocks++;
        } else if (node instanceof TableBlock) {
            counts.tables++;
        }
    }

    private static Set<Formatting> formattingOf(List<Segment> segments) {
        final Set<Formatting> formatting = EnumSet.noneOf(Formatting.class);
        for (final Segment segment : segments) {
            for (final String fragment : segment.placeholders().values()) {
                formatting.add(FormattingClassifier.classifyMarkdown(fragment));
            }
        }
        return formatting;
    }

    /**
     * Collects every resource id this Markdown carries (task 4.5): every image node's link destination.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public Set<String> resourceIds(Document document) {
        Objects.requireNonNull(document, "document");
        final ParsedMarkdown parsed = requireOpen(document);
        final Node root = astOf(parsed);
        final Set<String> destinations = new LinkedHashSet<>();
        collectImageDestinations(root, destinations);
        log.debug("Markdown resource ids documentId={} count={}", document.id(), destinations.size());
        return destinations;
    }

    private static void collectImageDestinations(Node node, Set<String> destinations) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Image image) {
                destinations.add(image.getDestination());
            }
            collectImageDestinations(child, destinations);
        }
    }

    private static Node astOf(ParsedMarkdown parsed) {
        final String text = new String(parsed.originalBytes(), parsed.charset());
        final Frontmatter.Split split = Frontmatter.split(text);
        return MarkdownReader.parser().parse(split.body());
    }

    private ParsedMarkdown requireOpen(Document document) {
        return registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));
    }
}
