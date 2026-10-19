package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.document.epub.RoleMarkers.Place;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.RawEntry;

/**
 * Places each spine document in the book's front matter, body or back matter from what the book itself says, in this
 * order: the navigation document's {@code landmarks}, the EPUB 2 guide, the document's own {@code epub:type}, and then
 * the labels the book gives it — its contents entry (when the entry names the whole file, not a place in it), its file
 * name and its first short line. A document whose only evidence is a label, and which holds at most
 * {@value #MAX_MATTER_SEGMENTS} segments, is matter outside the story, and lies in the front until the first story document and in the
 * back after it. A book that says nothing leaves every document {@link UnitRole#BODY}, so nothing is ever hidden
 * from a scan on a guess.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubUnitRoles {

    private static final String EPUB_TYPE = "epub:type";
    private static final String LANDMARKS = "landmarks";

    /** A first line this short, in a document this short, is its title, which is how an unlisted "Also by" page shows. */
    private static final int MAX_LABEL_WORDS = 6;

    private static final int MAX_MATTER_SEGMENTS = 40;

    /**
     * The units with their roles set.
     *
     * @param opf the parsed package
     * @param byName every archive entry by its archive-absolute path
     * @param units the spine documents' units in reading order
     * @param trees the parsed spine documents by their skeleton handle id
     * @return the same units in the same order, each with its role; never null
     */
    static List<Unit> assign(
            final ParsedOpf opf,
            final Map<String, RawEntry> byName,
            final List<Unit> units,
            final Map<String, Document> trees) {
        Objects.requireNonNull(opf, "opf");
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        final Map<String, Place> stated = statedPlaces(opf, byName, opfDir);
        final Map<String, String> labels = contentsLabels(opf, byName);
        final List<Unit> placed = new ArrayList<>(units.size());
        boolean storyStarted = false;
        for (final Unit unit : units) {
            final Place place =
                    placeOf(unit, stated, labels, trees.get(unit.skeleton().opaqueId()));
            final UnitRole role = roleOf(place, storyStarted);
            storyStarted |= role == UnitRole.BODY && !unit.segments().isEmpty();
            log.debug("EPUB unit {} place={} role={}", unit.href(), place, role);
            placed.add(unit.withRole(role));
        }
        return placed;
    }

    private static UnitRole roleOf(final Place place, final boolean storyStarted) {
        return switch (place) {
            case FRONT -> UnitRole.FRONT_MATTER;
            case BACK -> UnitRole.BACK_MATTER;
            case MATTER -> storyStarted ? UnitRole.BACK_MATTER : UnitRole.FRONT_MATTER;
            case BODY, NONE -> UnitRole.BODY;
        };
    }

    private static Place placeOf(
            final Unit unit,
            final Map<String, Place> stated,
            final Map<String, String> labels,
            final @Nullable Document tree) {
        final Place landmark = stated.getOrDefault(unit.href(), Place.NONE);
        if (landmark != Place.NONE) {
            return landmark;
        }
        final Place own = tree == null ? Place.NONE : ownType(tree);
        if (own != Place.NONE) {
            return own;
        }
        return matterByLabel(unit, labels.get(unit.href())) ? Place.MATTER : Place.NONE;
    }

    private static Place ownType(final Document tree) {
        final Element body = tree.body();
        final Place onBody = RoleMarkers.ofTypes(body.attr(EPUB_TYPE));
        if (onBody != Place.NONE) {
            return onBody;
        }
        final Element first = body.children().isEmpty() ? null : body.child(0);
        return first == null ? Place.NONE : RoleMarkers.ofTypes(first.attr(EPUB_TYPE));
    }

    // A label alone never makes a long document matter: a single-file book is named "Title Page" or "index" by its
    // first contents entry or its file, yet it is the story.
    private static boolean matterByLabel(final Unit unit, final @Nullable String contentsLabel) {
        if (unit.segments().size() > MAX_MATTER_SEGMENTS) {
            return false;
        }
        if (contentsLabel != null && RoleMarkers.ofLabel(contentsLabel) == Place.MATTER) {
            return true;
        }
        if (RoleMarkers.ofLabel(fileStem(unit.href())) == Place.MATTER) {
            return true;
        }
        return !unit.segments().isEmpty()
                && isShortLine(unit.segments().getFirst())
                && RoleMarkers.ofLabel(unit.segments().getFirst().masked()) == Place.MATTER;
    }

    private static boolean isShortLine(final Segment segment) {
        return RoleMarkers.wordCount(segment.masked()) <= MAX_LABEL_WORDS;
    }

    private static String fileStem(final String href) {
        final String name = href.substring(href.lastIndexOf('/') + 1);
        final int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static Map<String, Place> statedPlaces(
            final ParsedOpf opf, final Map<String, RawEntry> byName, final String opfDir) {
        final Map<String, Place> stated = new HashMap<>();
        for (final GuideReference reference : opf.guideReferences()) {
            put(stated, OpfPaths.resolve(opfDir, reference.href()), RoleMarkers.ofTypes(reference.type()));
        }
        final RawEntry navEntry = NavigationParser.findNavEntry(opf, byName, opfDir);
        if (navEntry != null) {
            final Document nav = XhtmlParser.parse(navEntry.content(), navEntry.name());
            final String navDir = OpfPaths.parentOf(navEntry.name());
            nav.select("nav").stream()
                    .filter(element -> element.attr(EPUB_TYPE).contains(LANDMARKS))
                    .flatMap(element -> element.select("a[href]").stream())
                    .forEach(link -> put(
                            stated,
                            OpfPaths.resolve(navDir, link.attr("href")),
                            RoleMarkers.ofTypes(link.attr(EPUB_TYPE))));
        }
        return stated;
    }

    private static void put(final Map<String, Place> stated, final String href, final Place place) {
        if (place != Place.NONE) {
            stated.putIfAbsent(href, place);
        }
    }

    private static Map<String, String> contentsLabels(final ParsedOpf opf, final Map<String, RawEntry> byName) {
        final Map<String, String> labels = new HashMap<>();
        try {
            collect(NavigationParser.parse(opf, byName).entries(), labels);
        } catch (CorruptContainerException e) {
            log.debug("EPUB contents unreadable, no contents labels used for roles: {}", e.getMessage());
        }
        return labels;
    }

    private static void collect(final List<NavigationParser.NavEntry> entries, final Map<String, String> labels) {
        for (final NavigationParser.NavEntry entry : entries) {
            // An entry that points into a file names a part of it, not the file.
            if (entry.fragment() == null) {
                labels.putIfAbsent(entry.href(), entry.label());
            }
            collect(entry.children(), labels);
        }
    }
}
