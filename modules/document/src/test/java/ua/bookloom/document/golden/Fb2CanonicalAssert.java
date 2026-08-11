package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Text;
import org.jdom2.input.SAXBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The FB2 canonical comparison: a canonical serialization of the re-parsed tree, <strong>plus</strong> the
 * declared encoding compared as a value, <strong>plus</strong> every {@code <binary>} payload compared exactly.
 *
 * <p><strong>The two extras are not belt-and-braces.</strong> A canonical writer normalises the XML declaration
 * away, so EC-FB2-1 — keep the declared encoding, or switch to UTF-8 and say so — would be unprovable through it.
 * And a canonical writer is free to re-wrap long text content, which would silently rewrite a base64 cover image
 * without breaking anything the structural comparison notices.
 *
 * <p>The encoding is compared as a <em>value</em> rather than as declaration text, because a real book writes
 * {@code encoding='utf-8'} with single quotes while a faithful writer emits double ones — that is a difference in
 * spelling, not in the document.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Fb2CanonicalAssert {

    private static final Pattern ENCODING_DECLARATION = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final String BINARY_ELEMENT = "binary";
    private static final String DESCRIPTION_ELEMENT = "description";
    private static final String TITLE_INFO_ELEMENT = "title-info";
    private static final String LANG_ELEMENT = "lang";
    private static final int MAX_LANGUAGE_INSERTION_LINES = 2;

    /**
     * Asserts that {@code actual} is canonical-XML-equal to {@code expected}, declares the same encoding value,
     * and carries every binary payload character-for-character.
     *
     * <p><strong>The one carve-out.</strong> When {@code expected}'s {@code title-info} declares no language (no
     * {@code <lang>} element, or one with no text), {@link ua.bookloom.document.fb2.Fb2Writer} is required to add
     * or replace it with {@code targetLanguage} — so canonical equality cannot hold there. In that case only, the
     * two canonical forms are compared as a <strong>bounded difference</strong>: nothing may be lost, and the only
     * gain permitted is the single {@code <lang>} element (or, when one already existed empty, its text) carrying
     * exactly {@code targetLanguage}. A second, unrelated difference — a dropped element anywhere else — still
     * fails, because it cannot be absorbed by that bound.
     *
     * @param expected the source fixture
     * @param actual the reassembled output
     * @param targetLanguage the language the write declared, checked against the carve-out's one permitted gain
     */
    static void assertCanonicalEqual(Path expected, Path actual, String targetLanguage) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final String expectedXml = xmlOf(expected);
        final String actualXml = xmlOf(actual);

        assertThat(encodingValueOf(actualXml)).as("declared encoding value").isEqualTo(encodingValueOf(expectedXml));
        assertThat(binaryPayloadsOf(actualXml))
                .as("binary payloads, character for character")
                .isEqualTo(binaryPayloadsOf(expectedXml));

        if (sourceDeclaresNoLanguage(expectedXml)) {
            assertBoundedToTheLanguageInsertion(expectedXml, actualXml, targetLanguage);
        } else {
            assertThat(canonicalize(actualXml)).as("canonical XML").isEqualTo(canonicalize(expectedXml));
        }
    }

    /**
     * {@code true} when the source has a {@code title-info} that declares no usable language — no {@code <lang>}
     * child, or one whose text is empty.
     *
     * <p>A source with no {@code <description>} or no {@code <title-info>} at all is deliberately NOT this case.
     * The writer's add-branch reaches for {@code title-info} and returns without doing anything when it is absent,
     * so no element is added and there is nothing for the carve-out to absorb — widening it to cover that shape
     * would relax the comparison for a document the writer never touches.
     */
    private static boolean sourceDeclaresNoLanguage(String expectedXml) {
        final Element titleInfo = firstChildByLocalName(
                firstChildByLocalName(parse(expectedXml), DESCRIPTION_ELEMENT), TITLE_INFO_ELEMENT);
        if (titleInfo == null) {
            return false;
        }
        final Element lang = firstChildByLocalName(titleInfo, LANG_ELEMENT);
        return lang == null || lang.getTextNormalize().isEmpty();
    }

    private static @Nullable Element firstChildByLocalName(@Nullable Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (final Element child : parent.getChildren()) {
            if (localName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Compares the two canonical forms as a bounded difference rather than by applying the writer's own
     * add-or-replace decision to {@code expected} — doing that would make this assertion agree with the writer
     * whatever it did. Instead, the two canonical line sequences are reduced to their common leading and trailing
     * lines; whatever remains between them is the entire difference, and it must consist of nothing lost from
     * {@code expected} and, from {@code actual}, at most the {@code <lang>} element line followed by its text
     * line — the shape a fixed-point insertion or an in-place text replacement produces.
     */
    private static void assertBoundedToTheLanguageInsertion(
            String expectedXml, String actualXml, String targetLanguage) {
        final List<String> expectedLines = canonicalize(expectedXml).lines().toList();
        final List<String> actualLines = canonicalize(actualXml).lines().toList();

        final int prefix = commonPrefixLength(expectedLines, actualLines);
        final int suffix = commonSuffixLength(expectedLines, actualLines, prefix);

        final List<String> lost = expectedLines.subList(prefix, expectedLines.size() - suffix);
        final List<String> gained = actualLines.subList(prefix, actualLines.size() - suffix);

        assertThat(lost)
                .as("canonical XML lines present in the source but missing from the output")
                .isEmpty();
        assertGainedIsExactlyTheLanguageInsertion(gained, targetLanguage);
    }

    private static void assertGainedIsExactlyTheLanguageInsertion(List<String> gained, String targetLanguage) {
        assertThat(gained)
                .as("canonical XML lines gained by the output, beyond the single target-language element")
                .isNotEmpty()
                .hasSizeLessThanOrEqualTo(MAX_LANGUAGE_INSERTION_LINES);
        assertThat(gained.get(gained.size() - 1).strip())
                .as("the gained <lang> element's text")
                .isEqualTo(targetLanguage);
        if (gained.size() == MAX_LANGUAGE_INSERTION_LINES) {
            assertThat(gained.get(0).strip())
                    .as("the gained <lang> element's opening line")
                    .isEqualTo("<" + LANG_ELEMENT + ">");
        }
    }

    private static int commonPrefixLength(List<String> expected, List<String> actual) {
        final int max = Math.min(expected.size(), actual.size());
        int i = 0;
        while (i < max && expected.get(i).equals(actual.get(i))) {
            i++;
        }
        return i;
    }

    private static int commonSuffixLength(List<String> expected, List<String> actual, int prefix) {
        final int max = Math.min(expected.size() - prefix, actual.size() - prefix);
        int i = 0;
        while (i < max && expected.get(expected.size() - 1 - i).equals(actual.get(actual.size() - 1 - i))) {
            i++;
        }
        return i;
    }

    /** Reads the FB2 text out of either a bare file or a zip container, decoded as it declares. */
    static String xmlOf(Path file) {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return ZipText.firstMemberEndingWith(file, ".fb2");
        }
        final byte[] bytes = readAll(file);
        final String prolog = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.ISO_8859_1);
        final Matcher matcher = ENCODING_DECLARATION.matcher(prolog);
        return new String(bytes, matcher.find() ? Charset.forName(matcher.group(1)) : StandardCharsets.UTF_8);
    }

    /** The encoding as a value, normalized for case, so {@code utf-8} and {@code UTF-8} are the same answer. */
    private static String encodingValueOf(String xml) {
        final Matcher matcher = ENCODING_DECLARATION.matcher(xml);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static List<String> binaryPayloadsOf(String xml) {
        final List<String> payloads = new ArrayList<>();
        collectBinaries(parse(xml), payloads);
        return payloads;
    }

    private static void collectBinaries(Element element, List<String> payloads) {
        if (BINARY_ELEMENT.equals(element.getName())) {
            payloads.add(element.getText());
            return;
        }
        for (final Element child : element.getChildren()) {
            collectBinaries(child, payloads);
        }
    }

    /**
     * A deterministic serialization of the tree — element name, attributes in sorted order, then children in
     * document order, with text whitespace-collapsed. Deliberately not JDOM's own outputter: the point is a form
     * that absorbs attribute quoting and entity spelling while still catching a lost element, a dropped comment
     * or a rewritten id.
     */
    private static String canonicalize(String xml) {
        final StringBuilder canonical = new StringBuilder();
        appendCanonical(parse(xml), canonical, 0);
        return canonical.toString();
    }

    private static void appendCanonical(Element element, StringBuilder canonical, int depth) {
        canonical.append("  ".repeat(depth)).append('<').append(element.getQualifiedName());
        element.getAttributes().stream()
                .map(a -> " " + a.getQualifiedName() + "=" + a.getValue())
                .sorted()
                .forEach(canonical::append);
        canonical.append(">\n");
        for (final Content child : element.getContent()) {
            appendCanonicalChild(child, canonical, depth);
        }
    }

    private static void appendCanonicalChild(Content child, StringBuilder canonical, int depth) {
        if (child instanceof Element childElement) {
            appendCanonical(childElement, canonical, depth + 1);
            return;
        }
        if (child instanceof Text text) {
            appendTextIfPresent(canonical, depth, text.getText());
            return;
        }
        if (child instanceof org.jdom2.Comment comment) {
            canonical
                    .append("  ".repeat(depth + 1))
                    .append("<!--")
                    .append(comment.getText())
                    .append("-->\n");
            return;
        }
        if (child instanceof org.jdom2.EntityRef entity) {
            canonical
                    .append("  ".repeat(depth + 1))
                    .append('&')
                    .append(entity.getName())
                    .append(";\n");
        }
    }

    private static void appendTextIfPresent(StringBuilder canonical, int depth, String raw) {
        final String collapsed = raw.replaceAll("\\s+", " ").strip();
        if (!collapsed.isEmpty()) {
            canonical.append("  ".repeat(depth + 1)).append(collapsed).append('\n');
        }
    }

    private static Element parse(String xml) {
        try {
            final SAXBuilder builder = new SAXBuilder();
            builder.setExpandEntities(false);
            builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return builder.build(new StringReader(xml)).getRootElement();
        } catch (JDOMException | IOException e) {
            throw new AssertionError("output is not well-formed XML", e);
        }
    }

    private static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
