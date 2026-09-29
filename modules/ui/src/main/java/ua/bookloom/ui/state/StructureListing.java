package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.StructureNode;

/**
 * What the structure screen shows for an opened book: one flat row per top-level node of its structure and, on request,
 * the sum of their segment counts. It is built here, away from the scene graph, so the numbers a person reads can be
 * checked without a window and cannot drift from the profile.
 *
 * @param rows the top-level nodes in navigation order, unmodifiable
 */
@Slf4j
public record StructureListing(List<StructureRow> rows) {

    /** Copies the rows so a caller's later change cannot alter what the screen shows. */
    public StructureListing {
        Objects.requireNonNull(rows, "rows");
        rows = List.copyOf(rows);
    }

    /**
     * Lists the top-level nodes of a book's structure.
     *
     * @param profile the opened book's profile; its structure is taken in the order it holds it
     * @return the rows and their total; no rows and a zero total when the book has no structure
     */
    public static StructureListing of(final BookProfile profile) {
        Objects.requireNonNull(profile, "profile");
        final List<StructureRow> rows =
                profile.structure().stream().map(StructureListing::rowOf).toList();
        log.debug("structure listing built with {} row(s)", rows.size());
        return new StructureListing(rows);
    }

    /**
     * Sums the rows rather than storing a figure, so the total cannot disagree with them.
     *
     * @return the segments across every row; zero when there are none
     */
    public int totalSegments() {
        return rows.stream().mapToInt(StructureRow::segmentCount).sum();
    }

    // A node that only points into a unit another node counts carries no figure of its own.
    private static StructureRow rowOf(final StructureNode node) {
        final Integer count = node.segmentCount();
        return new StructureRow(node.title(), count == null ? 0 : count);
    }
}
