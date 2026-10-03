package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The rates the corpus judge run is held to; an empty population counts as perfect. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalMetrics {

    /** Clean candidates the judge refused, over all clean candidates. */
    static double falsePositiveRate(final List<DefectRow> rows) {
        return share(rows, row -> !row.defective(), DefectRow::refused, 0.0);
    }

    /** Defective candidates the judge accepted, over all defective candidates. */
    static double falseNegativeRate(final List<DefectRow> rows) {
        return share(rows, DefectRow::defective, row -> !row.refused(), 0.0);
    }

    /** Cases whose repeated runs all gave one verdict. */
    static double stability(final List<DefectRow> rows) {
        return share(rows, row -> row.runs() > 1, DefectRow::stable, 1.0);
    }

    /** Cases whose judge replies all parsed. */
    static double parseRate(final List<DefectRow> rows) {
        return share(rows, row -> true, DefectRow::readable, 1.0);
    }

    private static double share(
            final List<DefectRow> rows,
            final Predicate<DefectRow> population,
            final Predicate<DefectRow> hit,
            final double empty) {
        final List<DefectRow> in = rows.stream().filter(population).toList();
        return in.isEmpty() ? empty : (double) in.stream().filter(hit).count() / in.size();
    }
}
