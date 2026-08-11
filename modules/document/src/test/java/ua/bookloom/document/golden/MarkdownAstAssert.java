package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/**
 * The Markdown comparison: a <strong>deterministic structural serialization</strong> of the re-parsed tree, and
 * never an equality check on two tree objects.
 *
 * <p>Syntax-tree nodes inherit identity equality, so {@code assertThat(a).isEqualTo(b)} on two ASTs compares two
 * references and passes only when they are the same object. A test written that way <em>fails to fail</em> — it
 * would report green against any output whatsoever, which is worse than having no test.
 *
 * <p>The serialization carries every field that affects rendering — a fence's info string, a heading's level, a
 * cell's alignment, a list's start number — because the whole reason to compare trees rather than text is that
 * Markdown has no canonical spelling, and a comparison that dropped those fields would be blind to real damage.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownAstAssert {

    /**
     * Asserts that re-parsing {@code actual} yields a tree structurally equal to {@code expected}'s, and that the
     * frontmatter block is byte-identical.
     *
     * @param expected the source fixture
     * @param actual the reassembled output
     */
    static void assertReParseEqual(Path expected, Path actual) {
        final String expectedText = readText(expected);
        final String actualText = readText(actual);

        assertThat(frontmatterOf(actualText))
                .as("frontmatter block, byte for byte")
                .isEqualTo(frontmatterOf(expectedText));
        assertThat(structureOf(actualText)).as("re-parsed CommonMark structure").isEqualTo(structureOf(expectedText));
    }

    /**
     * A depth-first rendering of node type, rendering-significant fields and literal text.
     *
     * @param markdown the document text
     * @return its structural serialization
     */
    static String structureOf(String markdown) {
        final Node parsed = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build()
                .parse(markdown);
        final StringBuilder structure = new StringBuilder();
        append(parsed, structure, 0);
        return structure.toString();
    }

    private static void append(Node node, StringBuilder structure, int depth) {
        structure
                .append("  ".repeat(depth))
                .append(node.getClass().getSimpleName())
                .append(describe(node))
                .append('\n');
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            append(child, structure, depth + 1);
        }
    }

    /** Everything about a node that changes what a reader sees, beyond its position in the tree. */
    private static String describe(Node node) {
        if (node instanceof Text text) {
            return " " + text.getLiteral();
        }
        if (node instanceof Code code) {
            return " `" + code.getLiteral() + "`";
        }
        if (node instanceof FencedCodeBlock fence) {
            return " info=" + fence.getInfo() + " literal=" + fence.getLiteral();
        }
        if (node instanceof IndentedCodeBlock indented) {
            return " literal=" + indented.getLiteral();
        }
        return describeStructural(node);
    }

    private static String describeStructural(Node node) {
        if (node instanceof HtmlBlock html) {
            return " literal=" + html.getLiteral();
        }
        if (node instanceof HtmlInline html) {
            return " literal=" + html.getLiteral();
        }
        if (node instanceof Heading heading) {
            return " level=" + heading.getLevel();
        }
        if (node instanceof TableCell cell) {
            return " header=" + cell.isHeader() + " align=" + cell.getAlignment();
        }
        if (node instanceof Link link) {
            return " dest=" + link.getDestination();
        }
        if (node instanceof LinkReferenceDefinition definition) {
            return " label=" + definition.getLabel() + " dest=" + definition.getDestination();
        }
        return describeList(node);
    }

    private static String describeList(Node node) {
        if (node instanceof OrderedList list) {
            return " start=" + list.getMarkerStartNumber();
        }
        if (node instanceof ListItem) {
            return "";
        }
        return "";
    }

    /** The leading {@code ---}-delimited block, or the empty string when the file opens with none. */
    private static String frontmatterOf(String text) {
        if (!text.startsWith("---\n")) {
            return "";
        }
        final int closing = text.indexOf("\n---", 3);
        if (closing < 0) {
            return "";
        }
        final int endOfLine = text.indexOf('\n', closing + 1);
        return endOfLine < 0 ? text : text.substring(0, endOfLine + 1);
    }

    private static String readText(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
