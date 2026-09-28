package ua.bookloom.document.epub;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;

/**
 * Keeps an XHTML content document's prolog — everything before the root element's start tag: the XML declaration,
 * comments, and the DOCTYPE — exactly as the source wrote it. jsoup reads the declaration as a bogus comment and
 * normalizes the DOCTYPE's quotes and line breaks, and a chapter in a non-UTF-8 encoding then no longer says which
 * encoding it is in (task 5.3).
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class XhtmlProlog {

    /**
     * Returns {@code tree}'s serialization with the source's own prolog ahead of the root element.
     *
     * @param tree the parsed document; never mutated
     * @param sourceText the document's source bytes decoded in the tree's charset
     * @return the prolog verbatim followed by the serialized root element (no prolog when the source had none)
     */
    static String serialize(Document tree, String sourceText) {
        final String prolog = of(sourceText);
        log.debug("XHTML prolog recorded={} length={}", !prolog.isEmpty(), prolog.length());
        return prolog + AttributeLineFeedEscaper.outerHtml(withoutPrologNodes(tree));
    }

    /**
     * Finds the prolog of a decoded document.
     *
     * @param sourceText the decoded document
     * @return the text before the first start tag, skipping comments, processing instructions and the DOCTYPE
     *     (with any internal subset); empty when the document starts at its root
     */
    static String of(String sourceText) {
        int pos = 0;
        while (pos < sourceText.length()) {
            final int start = skipWhitespace(sourceText, pos);
            final int end = markupEnd(sourceText, start);
            if (end < 0) {
                return sourceText.substring(0, start);
            }
            pos = end;
        }
        return "";
    }

    private static int skipWhitespace(String text, int from) {
        int pos = from;
        while (pos < text.length() && (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == '﻿')) {
            pos++;
        }
        return pos;
    }

    /** The index just past a comment, processing instruction or DOCTYPE starting at {@code start}, or -1. */
    private static int markupEnd(String text, int start) {
        if (text.startsWith("<!--", start)) {
            return afterMarker(text, "-->", start);
        }
        if (text.startsWith("<?", start)) {
            return afterMarker(text, "?>", start);
        }
        if (text.regionMatches(true, start, "<!DOCTYPE", 0, "<!DOCTYPE".length())) {
            return doctypeEnd(text, start);
        }
        return -1;
    }

    private static int afterMarker(String text, String marker, int start) {
        final int at = text.indexOf(marker, start);
        return at < 0 ? -1 : at + marker.length();
    }

    private static int doctypeEnd(String text, int start) {
        int depth = 0;
        char quote = 0;
        for (int i = start; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quote != 0) {
                quote = c == quote ? 0 : quote;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '>' && depth <= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private static Document withoutPrologNodes(Document tree) {
        final Node root = tree.selectFirst("html");
        if (root == null || root.siblingIndex() == 0) {
            return tree;
        }
        final Document copy = tree.clone();
        copy.outputSettings(tree.outputSettings());
        final Node copyRoot = copy.selectFirst("html");
        while (copyRoot != null && copyRoot.siblingIndex() > 0) {
            copy.childNode(0).remove();
        }
        return copy;
    }
}
