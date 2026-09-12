package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

/**
 * The canonical-equal comparison design.md D5 defines, pragmatically scoped to what {@code EpubWriter} actually
 * re-serializes: only XHTML spine documents and the OPF are ever rebuilt from a parsed tree, so every other entry
 * — {@code mimetype}, {@code META-INF/container.xml}, CSS, images, fonts, nav, NCX — is carried through as
 * untouched original bytes and can be compared byte-exact rather than through a bespoke canonical parser this
 * change never needs (task 5.3).
 *
 * <p>Deliberately decoupled from {@code ua.bookloom.document.epub}'s package-private helpers ({@code
 * ZipEntryReader}, {@code SecureXml}): this harness is meant to outlive EPUB-only scope, once change 4 needs the
 * same shape for FB2, Markdown and TXT (design.md D5), so it reads zip entries and builds its own JDOM2 parser
 * directly rather than reaching into EPUB internals.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EpubCanonicalAssert {

    private static final String MIMETYPE_ENTRY = "mimetype";
    private static final int FIRST_ENTRY_INDEX = 0;

    /**
     * Byte-for-byte duplicate of {@code XhtmlParser.SELF_CLOSED_ELEMENT} (design.md D2) — matches any element
     * written in XML self-closing form, for any tag name; whether a given match is rewritten is decided per match
     * by {@link #isKnownNonVoidTag(String)}, the duplicate of {@code XhtmlParser}'s identically-named method. The
     * attribute-value alternation ({@code "[^"]*"} / {@code '[^']*'}) exists so a quoted value containing
     * {@code >} or {@code /} — e.g. {@code src="js/book.js"} — is consumed as one unit instead of ending the
     * match early, and the reluctant quantifier then finds the first *unquoted* {@code />}: an ordinary open tag
     * such as {@code <script type="text/javascript">} never matches, because its attribute value is well-formed
     * but the tag itself closes with a bare {@code >}, not {@code />}. {@code \b} after each tag name rejects a
     * different element name sharing the prefix, e.g. {@code <scriptfoo/>}.
     */
    private static final Pattern SELF_CLOSED_ELEMENT =
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9-]*)\\b((?:\"[^\"]*\"|'[^']*'|[^\"'>])*?)/>");

    /**
     * Asserts that {@code output} is canonical-equal to {@code source}: same entry order apart from where the source
     * kept {@code mimetype} (a real book stores it third; the writer rightly moves it first), {@code mimetype} first
     * and STORED with identical content, every OPF entry canonical-XML-equal, every XHTML entry canonical-XHTML-
     * equal, and every other entry byte-for-byte identical (design.md D5).
     *
     * @param source the fixture archive a document was parsed from
     * @param output the archive the same document was reassembled to, with zero segment edits
     */
    public static void assertCanonicalEqual(Path source, Path output) {
        final List<ZipEntrySnapshot> sourceEntries = readZip(source);
        final List<ZipEntrySnapshot> outputEntries = readZip(output);

        assertThat(namesExceptMimetype(outputEntries))
                .as("entry order, mimetype aside")
                .containsExactlyElementsOf(namesExceptMimetype(sourceEntries));
        assertMimetypeFirstStoredAndExact(sourceEntries, outputEntries);
        assertRemainingEntriesCanonicalEqual(sourceEntries, outputEntries);
    }

    private static List<String> namesExceptMimetype(List<ZipEntrySnapshot> entries) {
        return names(entries).stream()
                .filter(name -> !MIMETYPE_ENTRY.equals(name))
                .toList();
    }

    private static void assertMimetypeFirstStoredAndExact(
            List<ZipEntrySnapshot> sourceEntries, List<ZipEntrySnapshot> outputEntries) {
        final ZipEntrySnapshot outputMimetype = outputEntries.get(FIRST_ENTRY_INDEX);
        assertThat(outputMimetype.name()).as("mimetype is the first entry").isEqualTo(MIMETYPE_ENTRY);
        assertThat(outputMimetype.method()).as("mimetype is STORED").isEqualTo(ZipEntry.STORED);
        assertThat(outputMimetype.content())
                .as("mimetype content")
                .isEqualTo(entryNamed(sourceEntries, MIMETYPE_ENTRY).content());
    }

    private static void assertRemainingEntriesCanonicalEqual(
            List<ZipEntrySnapshot> sourceEntries, List<ZipEntrySnapshot> outputEntries) {
        final Map<String, ZipEntrySnapshot> outputByName = byName(outputEntries);
        for (final ZipEntrySnapshot sourceEntry : sourceEntries) {
            if (MIMETYPE_ENTRY.equals(sourceEntry.name())) {
                continue;
            }
            final ZipEntrySnapshot outputEntry = outputByName.get(sourceEntry.name());
            assertThat(outputEntry)
                    .as("entry present in output: %s", sourceEntry.name())
                    .isNotNull();
            assertEntryCanonicalEqual(sourceEntry, outputEntry);
        }
    }

    private static void assertEntryCanonicalEqual(ZipEntrySnapshot source, ZipEntrySnapshot output) {
        final String lowerName = source.name().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".opf")) {
            assertThat(canonicalXml(output.content()))
                    .as("entry %s (canonical XML)", source.name())
                    .isEqualTo(canonicalXml(source.content()));
        } else if (lowerName.endsWith(".xhtml") || lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            assertThat(canonicalXhtml(output.content()))
                    .as("entry %s (canonical XHTML)", source.name())
                    .isEqualTo(canonicalXhtml(source.content()));
        } else {
            assertThat(output.content())
                    .as("entry %s (byte-exact)", source.name())
                    .isEqualTo(source.content());
        }
    }

    /**
     * Duplicates {@link ua.bookloom.document.epub}'s package-private {@code SecureXml.builder()} four lines
     * verbatim — this harness cannot call it from a different package, and a test-only XML parser is the lower-
     * risk place to accept the duplication rather than widening a production class's visibility for a test.
     */
    private static SAXBuilder secureXmlBuilder() {
        final SAXBuilder builder = new SAXBuilder();
        builder.setExpandEntities(false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return builder;
    }

    private static String canonicalXml(byte[] content) {
        try {
            final org.jdom2.Document parsed = secureXmlBuilder().build(new ByteArrayInputStream(content));
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            new XMLOutputter(Format.getRawFormat()).output(parsed, out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (JDOMException | IOException e) {
            throw new AssertionError("Unable to parse XML entry for canonical comparison", e);
        }
    }

    /**
     * Canonicalizes an XHTML entry for comparison. Both {@link #assertEntryCanonicalEqual} call sites — source
     * and output — route through this one method, so the {@link #expandSelfClosedNonVoidElement(String)} and
     * {@link #clearSeenSelfCloseOnNonVoidElements(Document)} rewrites applied here reach both sides identically
     * (design.md D2): a source holding {@code <script src="…"/>} or {@code <a id="x"/>} and an output holding the
     * writer's expanded {@code <script src="…"></script>} or {@code <a id="x"></a>} therefore compare equal, while
     * a genuine structural loss elsewhere in the same document still does not (proved by {@link
     * GoldenComparisonMetaTest}).
     */
    private static String canonicalXhtml(byte[] content) {
        final String normalized = expandSelfClosedNonVoidElement(new String(content, StandardCharsets.UTF_8));
        final Document parsed = Jsoup.parse(normalized);
        clearSeenSelfCloseOnNonVoidElements(parsed);
        parsed.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml);
        return parsed.outerHtml();
    }

    private static String expandSelfClosedNonVoidElement(String xhtml) {
        final Matcher matcher = SELF_CLOSED_ELEMENT.matcher(xhtml);
        final StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            final String tagName = matcher.group(1);
            final String replacement = isKnownNonVoidTag(tagName)
                    ? "<" + tagName + matcher.group(2) + "></" + tagName + ">"
                    : matcher.group();
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    private static boolean isKnownNonVoidTag(String tagName) {
        final String normalized = tagName.toLowerCase(Locale.ROOT);
        return Tag.isKnownTag(normalized) && !Tag.valueOf(normalized).isEmpty();
    }

    /**
     * Duplicate of {@code XhtmlParser}'s identically-named method (design.md D2) — clears {@code
     * Tag.SeenSelfClose} on every non-void element's tag so the comparator's own re-parse of raw source bytes
     * does not pick up self-close contagion from a self-closed tag production's parser deliberately leaves alone
     * (an unknown or void one), which would otherwise serialize differently from production's already-normalized
     * output for the same reason F12b did
     * ({@code docs/implementation_plan/notes-corpus-verification.md}).
     */
    private static void clearSeenSelfCloseOnNonVoidElements(Document doc) {
        for (final Element element : doc.getAllElements()) {
            final Tag tag = element.tag();
            if (!tag.isEmpty()) {
                tag.clear(Tag.SeenSelfClose);
            }
        }
    }

    private static List<String> names(List<ZipEntrySnapshot> entries) {
        return entries.stream().map(ZipEntrySnapshot::name).toList();
    }

    private static Map<String, ZipEntrySnapshot> byName(List<ZipEntrySnapshot> entries) {
        final Map<String, ZipEntrySnapshot> byName = new LinkedHashMap<>();
        for (final ZipEntrySnapshot entry : entries) {
            byName.put(entry.name(), entry);
        }
        return byName;
    }

    private static ZipEntrySnapshot entryNamed(List<ZipEntrySnapshot> entries, String name) {
        return byName(entries).get(name);
    }

    private static List<ZipEntrySnapshot> readZip(Path zip) {
        final List<ZipEntrySnapshot> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                entries.add(new ZipEntrySnapshot(entry.getName(), entry.getMethod(), in.readAllBytes()));
                entry = in.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw zip content read back purely for assertions,
    // same pattern as RawEntry/EpubZipBuilder.Entry — equals()/hashCode() are never used here.
    private record ZipEntrySnapshot(String name, int method, byte[] content) {

        private ZipEntrySnapshot {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
