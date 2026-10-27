package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.OpenMarkdownRegistry;

/**
 * A Markdown or FB2 book is one body unit cut into chapters, so each structure node names that unit and the position of
 * its first segment: picking a chapter on Import lists that chapter's segments, not none and not the whole book.
 */
class SingleUnitStructurePartsTest {

    @TempDir
    Path tempDir;

    // IF a Markdown chapter did not say where its segments start, THEN picking it could list nothing at all.
    @Test
    void structure_markdownHeadings_eachNodeNamesTheUnitAndItsFirstSegment() {
        final Path markdown = write(
                tempDir.resolve("nested.md"),
                "Foreword.\n\n# Part One\n\nIntro.\n\n## Chapter 1\n\nA.\n\n## Chapter 2\n\nB.\n");
        final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();
        final List<StructureNode> nodes =
                new MarkdownInspection(registry).structure(new MarkdownReader(registry).read(markdown));

        assertThat(nodes)
                .extracting(StructureNode::unitId, StructureNode::firstSegment, StructureNode::segmentCount)
                .containsExactly(tuple("nested.md", 0, 1), tuple("nested.md", 1, 6));
        assertThat(nodes.get(1).children())
                .extracting(StructureNode::unitId, StructureNode::firstSegment, StructureNode::segmentCount)
                .containsExactly(tuple("nested.md", 3, 2), tuple("nested.md", 5, 2));
    }

    // IF an FB2 section did not say where its segments start, THEN picking it could list nothing at all; a notes body
    // is a unit of its own and stands for it whole.
    @Test
    void structure_fb2Sections_eachNodeNamesTheUnitAndItsFirstSegment() {
        final String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><book-title>Sample</book-title></title-info></description>
                  <body>
                    <epigraph><p>Epigraph line.</p></epigraph>
                    <section>
                      <title><p>Part One</p></title>
                      <section><title><p>Chapter 1</p></title><p>A.</p></section>
                      <section><title><p>Chapter 2</p></title><p>B.</p><p>C.</p></section>
                    </section>
                  </body>
                  <body name="notes"><p>Note one.</p></body>
                </FictionBook>
                """;
        final Path fb2 = Fb2Fixtures.writeFb2(tempDir.resolve("parts.fb2"), xml, StandardCharsets.UTF_8);
        final OpenFb2Registry registry = new OpenFb2Registry();
        final List<StructureNode> nodes = new Fb2Inspection(registry).structure(new Fb2Reader(registry).read(fb2));

        assertThat(nodes)
                .extracting(StructureNode::unitId, StructureNode::firstSegment, StructureNode::segmentCount)
                .containsExactly(tuple("parts.fb2#0", 0, 1), tuple("parts.fb2#0", 1, 6), tuple("parts.fb2#1", null, 1));
        assertThat(nodes.get(1).children())
                .extracting(StructureNode::firstSegment, StructureNode::segmentCount)
                .containsExactly(tuple(2, 2), tuple(4, 3));
    }

    private static Path write(final Path destination, final String content) {
        try {
            return Files.writeString(destination, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
