package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.inspect.BodySegments;
import ua.bookloom.document.inspect.FormattingClassifier;
import ua.bookloom.document.inspect.WordCounter;

/**
 * Computes an EPUB's statistics and resource ids (task 4.5) from its already-parsed spine content, split out of
 * {@link EpubInspection} the way {@link EpubStructureBuilder} already splits out structure — so neither class
 * grows past the project's file-length limit.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubStats {

    private static final String IMAGE_MEDIA_PREFIX = "image/";
    private static final String PRE_TAG = "pre";
    private static final String TABLE_TAG = "table";
    private static final String EPUB_TYPE_ATTRIBUTE = "epub:type";
    private static final String HREF_ATTRIBUTE = "href";
    private static final Pattern FOOTNOTE_OR_ENDNOTE_TOKEN = Pattern.compile("(?:^|\\s)(?:footnote|endnote)(?:\\s|$)");

    /**
     * Computes {@code document}'s statistics from {@code parsed}'s spine content and {@code opf}'s manifest.
     *
     * @param document the open document, whose body segments are counted
     * @param parsed the registry-held parsed EPUB state
     * @param opf the parsed OPF, or {@code null} when its raw entry could not be found (an unreachable manifest
     *     yields zero images/fonts rather than a failure)
     * @return the computed statistics
     */
    static BookStats compute(Document document, ParsedEpub parsed, @Nullable ParsedOpf opf) {
        final List<Segment> bodySegments = BodySegments.of(document);
        final int words = WordCounter.count(bodySegments, document.declaredLang());
        final int verseLines = countByKind(bodySegments, SegmentKind.VERSE_LINE);
        final Set<Formatting> formatting = formattingOf(bodySegments);
        if (opf == null) {
            return new BookStats(bodySegments.size(), words, 0, 0, 0, verseLines, 0, 0, formatting);
        }
        final List<org.jsoup.nodes.Document> bodyTrees = bodyTreesOf(document, parsed);
        return new BookStats(
                bodySegments.size(),
                words,
                countManifest(opf, item -> item.mediaType().startsWith(IMAGE_MEDIA_PREFIX)),
                countElements(bodyTrees, PRE_TAG),
                countManifest(opf, item -> FontMediaTypes.isFont(item.mediaType())),
                verseLines,
                countFootnotes(bodyTrees),
                countElements(bodyTrees, TABLE_TAG),
                formatting);
    }

    /**
     * Collects {@code document}'s resource ids: {@code opf}'s image/font manifest ids, plus every id an internal
     * link fragment targets anywhere in {@code parsed}'s spine content.
     *
     * @param document the open document, whose spine units are scanned for internal link fragments
     * @param parsed the registry-held parsed EPUB state
     * @param opf the parsed OPF, or {@code null} when its raw entry could not be found
     * @return the resource ids; never null, empty when {@code opf} is {@code null}
     */
    static Set<String> resourceIds(Document document, ParsedEpub parsed, @Nullable ParsedOpf opf) {
        if (opf == null) {
            return Set.of();
        }
        final Set<String> ids = new LinkedHashSet<>();
        for (final ManifestItem item : opf.manifestItems()) {
            if (item.mediaType().startsWith(IMAGE_MEDIA_PREFIX) || FontMediaTypes.isFont(item.mediaType())) {
                ids.add(item.id());
            }
        }
        ids.addAll(hrefFragmentTargets(bodyTreesOf(document, parsed)));
        return ids;
    }

    private static int countByKind(List<Segment> segments, SegmentKind kind) {
        int count = 0;
        for (final Segment segment : segments) {
            if (segment.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    private static Set<Formatting> formattingOf(List<Segment> segments) {
        final Set<Formatting> formatting = EnumSet.noneOf(Formatting.class);
        for (final Segment segment : segments) {
            for (final String fragment : segment.placeholders().values()) {
                final Formatting kind = FormattingClassifier.classifyTag(fragment);
                if (kind != null) {
                    formatting.add(kind);
                }
            }
        }
        return formatting;
    }

    private static int countManifest(ParsedOpf opf, Predicate<ManifestItem> predicate) {
        int count = 0;
        for (final ManifestItem item : opf.manifestItems()) {
            if (predicate.test(item)) {
                count++;
            }
        }
        return count;
    }

    private static int countElements(List<org.jsoup.nodes.Document> trees, String tag) {
        int count = 0;
        for (final org.jsoup.nodes.Document tree : trees) {
            count += tree.body().select(tag).size();
        }
        return count;
    }

    private static int countFootnotes(List<org.jsoup.nodes.Document> trees) {
        int count = 0;
        for (final org.jsoup.nodes.Document tree : trees) {
            for (final Element element : tree.body().getAllElements()) {
                if (isFootnoteType(element)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isFootnoteType(Element element) {
        final String epubType = element.attr(EPUB_TYPE_ATTRIBUTE);
        return !epubType.isEmpty()
                && FOOTNOTE_OR_ENDNOTE_TOKEN.matcher(" " + epubType + " ").find();
    }

    private static Set<String> hrefFragmentTargets(List<org.jsoup.nodes.Document> trees) {
        final Set<String> targets = new LinkedHashSet<>();
        for (final org.jsoup.nodes.Document tree : trees) {
            for (final Element element : tree.body().select("[" + HREF_ATTRIBUTE + "]")) {
                final String href = element.attr(HREF_ATTRIBUTE);
                final int fragmentStart = href.indexOf('#');
                if (fragmentStart >= 0 && fragmentStart + 1 < href.length()) {
                    targets.add(href.substring(fragmentStart + 1));
                }
            }
        }
        return targets;
    }

    private static List<org.jsoup.nodes.Document> bodyTreesOf(Document document, ParsedEpub parsed) {
        final List<org.jsoup.nodes.Document> trees = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary()) {
                continue;
            }
            final org.jsoup.nodes.Document tree =
                    parsed.spineTreesByHandleId().get(unit.skeleton().opaqueId());
            if (tree != null) {
                trees.add(tree);
            }
        }
        return trees;
    }
}
