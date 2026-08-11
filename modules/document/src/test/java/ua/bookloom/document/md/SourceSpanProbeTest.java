package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The probe design.md required <strong>before</strong> the Markdown walker was written against source spans: do
 * they exist, and in particular does {@code TableCell} report one? Cell spans were the one unverified dependency
 * of the whole Markdown design, and tables are the most common structure in the surveyed corpus.
 *
 * <p><strong>Result: cell spans exist and land exactly on the cell.</strong> The design.md fallback — locating
 * cells by scanning for unescaped pipes within the row's span — is not needed and was not built.
 *
 * <p>Kept as a standing test because two of its findings are load-bearing and would regress silently:
 *
 * <ul>
 *   <li>A <strong>block's own span includes its marker</strong>. A heading's span starts at the {@code #}, and a
 *       table cell's span includes the padding spaces around the text. Replacing a block's own span would delete
 *       the {@code #} from every heading in the book. The replacement range is therefore the union of the block's
 *       <em>inline</em> spans, which is what {@link MarkdownSpans} computes.
 *   <li><strong>Frontmatter is not frontmatter to this parser.</strong> A leading {@code ---} block parses as a
 *       thematic break followed by a setext heading, so it must be split off before the parser sees it — which is
 *       exactly what {@link Frontmatter} does.
 * </ul>
 */
class SourceSpanProbeTest {

    private static final String TABLE_DOC = """
            | Left | Center | Right |
            |:-----|:------:|------:|
            | a1   | b1     | c1    |
            """;

    private static final String HEADING_DOC = "# Heading\n";

    private static Node parse(String markdown) {
        return Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build()
                .parse(markdown);
    }

    private static String spanTextOf(Node node, String source) {
        final List<SourceSpan> spans = node.getSourceSpans();
        final SourceSpan first = spans.get(0);
        final SourceSpan last = spans.get(spans.size() - 1);
        return source.substring(first.getInputIndex(), last.getInputIndex() + last.getLength());
    }

    private static Node findFirst(Node from, Class<? extends Node> type) {
        return java.util.Objects.requireNonNull(
                findFirstOrNull(from, type), () -> "no " + type.getSimpleName() + " in the parsed tree");
    }

    private static @Nullable Node findFirstOrNull(Node from, Class<? extends Node> type) {
        for (Node child = from.getFirstChild(); child != null; child = child.getNext()) {
            if (type.isInstance(child)) {
                return child;
            }
            final Node found = findFirstOrNull(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // Covers: FR-DOC-MD-4 — a table cell reports a source span that lands on its own text, so cells can be
    // addressed by byte span without scanning the row for delimiters.
    @Test
    void parse_tableCell_reportsASourceSpanCoveringItsOwnText() {
        final Node cell = findFirst(parse(TABLE_DOC), org.commonmark.ext.gfm.tables.TableCell.class);

        assertThat(cell).isNotNull();
        assertThat(spanTextOf(cell, TABLE_DOC)).isEqualTo(" Left ");
    }

    /**
     * The finding that made the union-of-inline-spans rule necessary: a heading's own span starts at the
     * {@code #}, so replacing it would delete the marker from every heading in a translated book.
     */
    @Test
    void parse_heading_ownSpanIncludesTheMarkerWhileItsInlineSpanDoesNot() {
        final Node heading = findFirst(parse(HEADING_DOC), Heading.class);

        assertThat(spanTextOf(heading, HEADING_DOC)).isEqualTo("# Heading");
        assertThat(spanTextOf(heading.getFirstChild(), HEADING_DOC)).isEqualTo("Heading");
    }

    /**
     * The finding that makes {@link Frontmatter} necessary: without splitting it off first, a leading
     * {@code ---} block is an ordinary thematic break and the key/value lines become a setext heading — that is,
     * translatable prose.
     */
    @Test
    void parse_leadingDelimitedBlock_isNotRecognisedAsFrontmatterByTheParser() {
        final String withFrontmatter = "---\ntitle: The Book\n---\n\nProse.\n";

        final Node parsed = parse(withFrontmatter);

        assertThat(findFirst(parsed, Heading.class)).isNotNull();
        assertThat(findFirst(parsed, Paragraph.class)).isNotNull();
    }
}
