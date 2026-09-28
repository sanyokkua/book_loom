package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;

/**
 * What the structure screen shows for an opened book: one flat row per unit in reading order and, on request, the sum of
 * their segment counts. It is built here, away from the scene graph, so the numbers a person reads can be checked without a
 * window and cannot drift from the parse.
 *
 * @param rows the units in reading order, unmodifiable
 */
@Slf4j
public record StructureListing(List<StructureRow> rows) {

    /** Copies the rows so a caller's later change cannot alter what the screen shows. */
    public StructureListing {
        Objects.requireNonNull(rows, "rows");
        rows = List.copyOf(rows);
    }

    /**
     * Lists the units of a parsed book.
     *
     * @param document the opened book; its units are taken in the order it holds them
     * @return the rows and their total; no rows and a zero total when the book has no units
     */
    public static StructureListing of(final Document document) {
        Objects.requireNonNull(document, "document");
        final List<StructureRow> rows = document.units().stream()
                .filter(unit -> !isLeftOut(unit))
                .map(StructureListing::rowOf)
                .toList();
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

    /** The auxiliary unit is not a section of the book; it is left out until the brief's switches reach the run. */
    private static boolean isLeftOut(final Unit unit) {
        if (unit.isAuxiliary()) {
            log.debug(
                    "structure listing leaves the auxiliary unit out segments={}",
                    unit.segments().size());
        }
        return unit.isAuxiliary();
    }

    private static StructureRow rowOf(final Unit unit) {
        return new StructureRow(unit.href(), unit.order(), unit.segments().size());
    }
}
