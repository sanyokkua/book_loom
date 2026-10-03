package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * One labelled judge case of the regression corpus.
 *
 * @param id the case id, the first column of the report
 * @param kind the defect family (garbled, quotes, gender …)
 * @param source the source sentence
 * @param candidate the Ukrainian candidate shown to the judge
 * @param defective whether the candidate has a real defect the judge must refuse
 * @param rationale why the label is what it is
 */
record DefectCase(String id, String kind, String source, String candidate, boolean defective, String rationale) {

    /** Rejects missing parts. */
    DefectCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(rationale, "rationale");
    }
}
