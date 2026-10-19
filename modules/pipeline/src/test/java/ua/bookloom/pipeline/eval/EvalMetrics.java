package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The rates the corpus reviewer run is held to; an empty population counts as perfect. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalMetrics {

    /** Clean candidates the reviewer would have changed (false-alarm edits), over all clean candidates. */
    static double falsePositiveRate(final List<DefectRow> rows) {
        return share(rows, row -> !row.defective(), DefectRow::flagged, 0.0);
    }

    /** Defective candidates the reviewer left alone, over all defective candidates. */
    static double falseNegativeRate(final List<DefectRow> rows) {
        return share(rows, DefectRow::defective, row -> !row.flagged(), 0.0);
    }

    /** Defective candidates the reviewer asked to change, over all defective candidates: the defect catch rate. */
    static double catchRate(final List<DefectRow> rows) {
        return share(rows, DefectRow::defective, DefectRow::flagged, 1.0);
    }

    /** Applied edits or rewrites that changed a placeholder token, summed over every case. */
    static int tokenBreaks(final List<DefectRow> rows) {
        return rows.stream().mapToInt(DefectRow::tokenBreaks).sum();
    }

    /** Cases whose repeated runs all asked for the same change. */
    static double stability(final List<DefectRow> rows) {
        return share(rows, row -> row.runs() > 1, DefectRow::stable, 1.0);
    }

    /** Cases whose reviewer replies all parsed. */
    static double parseRate(final List<DefectRow> rows) {
        return share(rows, row -> true, DefectRow::readable, 1.0);
    }

    /**
     * Per defect family, the share of cases that came out right — flagged exactly when defective — in the order the
     * families first appear.
     */
    static Map<String, Double> rightByKind(final List<DefectRow> rows) {
        final Map<String, List<DefectRow>> byKind = new LinkedHashMap<>();
        rows.forEach(row ->
                byKind.computeIfAbsent(row.kind(), kind -> new ArrayList<>()).add(row));
        final Map<String, Double> rates = new LinkedHashMap<>();
        byKind.forEach((kind, own) ->
                rates.put(kind, share(own, row -> true, row -> row.flagged() == row.defective(), 1.0)));
        return rates;
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
