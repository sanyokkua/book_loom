package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

/**
 * Lists, position by position, every attribute value and text run that differs between two XML documents of one
 * shape. It reports what changed rather than asserting equality, so a test can say "exactly these values, and the
 * language metadata".
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class XmlLeafDiff {

    /** One value that differs; {@code path} names the element chain and, for an attribute, {@code @name}. */
    record Change(String path, String oldValue, String newValue) {

        boolean isLanguage() {
            return path.toLowerCase(java.util.Locale.ROOT).contains("lang");
        }
    }

    /** The differing leaves, asserting first that both documents have the same number of them. */
    static List<Change> between(byte[] source, byte[] output) {
        final List<String[]> before = leaves(source);
        final List<String[]> after = leaves(output);
        assertThat(after).as("value count").hasSameSizeAs(before);
        final List<Change> changes = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i)[0]).as("leaf %d path", i).isEqualTo(before.get(i)[0]);
            if (!before.get(i)[1].equals(after.get(i)[1])) {
                changes.add(new Change(before.get(i)[0], before.get(i)[1], after.get(i)[1]));
            }
        }
        return changes;
    }

    private static List<String[]> leaves(byte[] xml) {
        try {
            final org.jsoup.nodes.Document parsed =
                    Jsoup.parse(new ByteArrayInputStream(xml), null, "", Parser.xmlParser());
            final List<String[]> leaves = new ArrayList<>();
            for (final Element element : parsed.getAllElements()) {
                final String path = pathOf(element);
                element.attributes().forEach(a -> leaves.add(new String[] {path + "@" + a.getKey(), a.getValue()}));
                element.textNodes().stream()
                        .map(TextNode::text)
                        .filter(text -> !text.isBlank())
                        .forEach(text -> leaves.add(new String[] {path + "#text", text.strip()}));
            }
            return leaves;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String pathOf(Element element) {
        final StringBuilder path = new StringBuilder(element.tagName());
        for (Element parent = element.parent(); parent != null; parent = parent.parent()) {
            path.insert(0, parent.tagName() + ">");
        }
        return path.toString();
    }
}
