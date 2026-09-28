package ua.bookloom.document.epub;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Serializes a content document while keeping a line feed, carriage return or tab inside an attribute value as a
 * character reference. An XML reader turns a raw one into a space, so writing the line-feed reference back as a raw line feed
 * silently changes the value; jsoup decodes the reference into the character and cannot spell it again, so the
 * only place to put it back is the serialized output: the three characters are swapped for private-use sentinels
 * on a copy of the tree, and each sentinel in the output becomes its reference (task 5.2).
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AttributeLineFeedEscaper {

    private static final char LINE_FEED_SENTINEL = '';
    private static final char CARRIAGE_RETURN_SENTINEL = '';
    private static final char TAB_SENTINEL = '';

    /** A sentinel as jsoup may write it: the character itself, or a hex reference when the charset cannot hold it. */
    private static final Pattern SENTINEL_IN_OUTPUT = Pattern.compile("[]|&#x[eE]00[9aAdD];");

    /**
     * Returns {@code tree}'s serialization, with each line feed, carriage return and tab inside an attribute value
     * written as a decimal character reference (10, 13 and 9). A document that already holds one of the sentinel
     * code points anywhere is written as jsoup writes it, since the swap could not tell its own sentinels apart.
     *
     * @param tree the tree to serialize; never mutated
     * @return the serialized document
     */
    static String outerHtml(Document tree) {
        final long escapable = tree.getAllElements().stream()
                .flatMap(element -> element.attributes().asList().stream())
                .filter(attribute -> needsEscape(attribute.getValue()))
                .count();
        final String plain = tree.outerHtml();
        if (escapable == 0) {
            log.debug("attribute values escaped: 0");
            return plain;
        }
        if (SENTINEL_IN_OUTPUT.matcher(plain).find()) {
            log.debug("attribute line-feed post-pass skipped: the document already holds a sentinel code point");
            return plain;
        }
        log.debug("attribute values escaped: {}", escapable);
        final Document copy = tree.clone();
        copy.outputSettings(tree.outputSettings());
        for (final Element element : copy.getAllElements()) {
            for (final Attribute attribute : element.attributes()) {
                attribute.setValue(swapForSentinels(attribute.getValue()));
            }
        }
        return restoreReferences(copy.outerHtml());
    }

    private static boolean needsEscape(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0;
    }

    private static String swapForSentinels(String value) {
        return value.replace('\n', LINE_FEED_SENTINEL)
                .replace('\r', CARRIAGE_RETURN_SENTINEL)
                .replace('\t', TAB_SENTINEL);
    }

    private static String restoreReferences(String serialized) {
        return serialized
                .replaceAll("|&#x[eE]00[aA];", "&#10;")
                .replaceAll("|&#x[eE]00[dD];", "&#13;")
                .replaceAll("|&#x[eE]009;", "&#9;");
    }
}
