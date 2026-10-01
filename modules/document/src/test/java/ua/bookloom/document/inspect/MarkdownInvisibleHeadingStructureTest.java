package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.OpenMarkdownRegistry;

/** A Markdown heading a reader cannot see is no segment, so it must not lend its level to the next heading. */
class MarkdownInvisibleHeadingStructureTest {

    @TempDir
    private Path tempDir;

    @Test
    void structure_invisibleHeading_levelsStayWithTheirOwnHeadings() throws IOException {
        final Path markdown = tempDir.resolve("invisible.md");
        Files.writeString(markdown, "# ​\n\n## A\n\nx\n\n# B\n\ny\n", StandardCharsets.UTF_8);
        final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();

        final List<StructureNode> nodes =
                new MarkdownInspection(registry).structure(new MarkdownReader(registry).read(markdown));

        assertThat(nodes).extracting(StructureNode::title).containsExactly("A", "B");
        assertThat(nodes).extracting(StructureNode::segmentCount).containsExactly(2, 2);
    }
}
