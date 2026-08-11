package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

/**
 * {@code OpenEpubRegistry}'s put/find round trip — the seam a later change's {@code write()} uses to find the
 * parsed trees a {@code SkeletonHandle} names (design.md D1).
 */
class OpenEpubRegistryTest {

    @Test
    void find_afterPut_returnsTheRegisteredState() {
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final ParsedEpub parsed = new ParsedEpub(
                List.of(), "OEBPS/content.opf", new org.jdom2.Document(new Element("package")), Map.of());

        registry.put("doc-1", parsed);

        assertThat(registry.find("doc-1")).isPresent().hasValue(parsed);
    }

    @Test
    void find_unknownId_isEmpty() {
        final OpenEpubRegistry registry = new OpenEpubRegistry();

        assertThat(registry.find("never-registered")).isEmpty();
    }
}
