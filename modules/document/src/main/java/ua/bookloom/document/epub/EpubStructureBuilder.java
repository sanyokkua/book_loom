package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.epub.NavigationParser.NavEntry;

/**
 * Maps an EPUB's navigation entries onto its spine units (task 4.4): each spine unit is counted once, under the
 * first entry that reaches it, with every later entry into the same unit carrying no count of its own; a spine
 * unit no entry reaches becomes a top-level node at its own spine position.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubStructureBuilder {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("⟦g\\d+⟧");

    /**
     * Builds the top-level structure nodes, merging the navigation-derived tree with every spine unit no entry
     * reaches, in overall spine reading position.
     *
     * @param navigation the navigation entries this book declares
     * @param document the open document, whose units carry the real segments to count
     * @return the top-level nodes, in reading order
     */
    static List<StructureNode> build(NavigationParser.Result navigation, Document document) {
        Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(document, "document");
        final Map<String, Unit> unitsByHref = unitsByHref(document);
        final Set<String> reachedUnitIds = new HashSet<>();
        final IdentityHashMap<NavEntry, Boolean> ownership = new IdentityHashMap<>();
        markOwnership(navigation.entries(), unitsByHref, reachedUnitIds, ownership);
        return placeTopLevelNodes(navigation.entries(), document, unitsByHref, ownership, reachedUnitIds);
    }

    private static Map<String, Unit> unitsByHref(Document document) {
        final Map<String, Unit> byHref = new LinkedHashMap<>();
        for (final Unit unit : document.units()) {
            if (!unit.isAuxiliary()) {
                byHref.put(unit.href(), unit);
            }
        }
        return byHref;
    }

    /** Marks, in navigation document order, the one entry that first reaches each distinct spine unit. */
    private static void markOwnership(
            List<NavEntry> entries,
            Map<String, Unit> unitsByHref,
            Set<String> reached,
            IdentityHashMap<NavEntry, Boolean> ownership) {
        for (final NavEntry entry : entries) {
            final Unit unit = unitsByHref.get(entry.href());
            ownership.put(entry, unit != null && reached.add(unit.id()));
            markOwnership(entry.children(), unitsByHref, reached, ownership);
        }
    }

    private record BuildResult(StructureNode node, int aggregate) {}

    private static BuildResult buildNode(
            NavEntry entry, Map<String, Unit> unitsByHref, IdentityHashMap<NavEntry, Boolean> ownership) {
        final Unit unit = unitsByHref.get(entry.href());
        final List<BuildResult> childResults = entry.children().stream()
                .map(child -> buildNode(child, unitsByHref, ownership))
                .toList();
        final int childrenAggregate =
                childResults.stream().mapToInt(BuildResult::aggregate).sum();
        final boolean owns = Boolean.TRUE.equals(ownership.get(entry));
        final int aggregate = (owns && unit != null ? unit.segments().size() : 0) + childrenAggregate;
        final Integer displayed = displayedCountOf(unit, owns, aggregate);
        final List<StructureNode> children =
                childResults.stream().map(BuildResult::node).toList();
        final String unitId = unit == null ? null : unit.id();
        return new BuildResult(new StructureNode(entry.label(), unitId, displayed, children), aggregate);
    }

    /**
     * A node whose own unit is already counted under an earlier entry shows no count of its own; a purely
     * organizational entry (no unit of its own) shows the aggregate of what its subtree reached, or none when it
     * reached nothing.
     */
    private static @Nullable Integer displayedCountOf(@Nullable Unit unit, boolean owns, int aggregate) {
        if (unit != null) {
            return owns ? aggregate : null;
        }
        return aggregate > 0 ? aggregate : null;
    }

    private record Placed(int position, int tiebreak, StructureNode node) {}

    private static List<StructureNode> placeTopLevelNodes(
            List<NavEntry> topEntries,
            Document document,
            Map<String, Unit> unitsByHref,
            IdentityHashMap<NavEntry, Boolean> ownership,
            Set<String> reachedUnitIds) {
        final List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < topEntries.size(); i++) {
            final NavEntry entry = topEntries.get(i);
            final BuildResult result = buildNode(entry, unitsByHref, ownership);
            placed.add(new Placed(spinePositionOf(entry, unitsByHref), i, result.node()));
        }
        int fallbackTiebreak = topEntries.size();
        for (final Unit unit : document.units()) {
            if (!unit.isAuxiliary() && !reachedUnitIds.contains(unit.id())) {
                placed.add(new Placed(unit.order(), fallbackTiebreak++, fallbackNode(unit)));
            }
        }
        placed.sort(Comparator.comparingInt(Placed::position).thenComparingInt(Placed::tiebreak));
        return placed.stream().map(Placed::node).toList();
    }

    /** An entry's own unit position, else the earliest position reached anywhere within its subtree. */
    private static int spinePositionOf(NavEntry entry, Map<String, Unit> unitsByHref) {
        final Unit ownUnit = unitsByHref.get(entry.href());
        if (ownUnit != null) {
            return ownUnit.order();
        }
        int minOrder = Integer.MAX_VALUE;
        for (final NavEntry child : entry.children()) {
            minOrder = Math.min(minOrder, spinePositionOf(child, unitsByHref));
        }
        return minOrder;
    }

    private static StructureNode fallbackNode(Unit unit) {
        final String title = firstHeadingText(unit).orElseGet(() -> fileNameOf(unit.href()));
        return new StructureNode(title, unit.id(), unit.segments().size(), List.of());
    }

    private static java.util.Optional<String> firstHeadingText(Unit unit) {
        return unit.segments().stream()
                .filter(segment -> segment.kind() == SegmentKind.HEADING)
                .map(segment -> PLACEHOLDER_TOKEN
                        .matcher(segment.sourceInner())
                        .replaceAll("")
                        .strip())
                .filter(text -> !text.isEmpty())
                .findFirst();
    }

    private static String fileNameOf(String href) {
        final int slash = href.lastIndexOf('/');
        return slash < 0 ? href : href.substring(slash + 1);
    }
}
