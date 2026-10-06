package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.EvalContext.Pair;

/**
 * The batches of the A/B: consecutive runs of the fixed English → Ukrainian draft cases, so the number-, symbol- and
 * Roman-only lines, the drop cap, the footnote and the locked names fall into batches as they do in a book. A batch of
 * a size starts where the previous one ended; a size that does not divide the case list leaves its tail out. The
 * context of a batch — its previous pairs, the source after it, its glossary lines — is not written here: the run's own
 * {@link EvalProject} makes it from the cases.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchEvalCases {

    /** The batch sizes of the sweep; the same four the controller's bounds are chosen from. */
    static final List<Integer> SIZES = List.of(4, 8, 12, 16);

    private static final List<Pair> EARLIER_PAIRS =
            List.of(new Pair("It was late.", "Було пізно."), new Pair("He left.", "Він пішов."));

    /**
     * One batch of the sweep.
     *
     * @param cases the draft cases that fill it, in order
     * @param next the case after the batch, whose source the run shows for pronouns and gender, or null at the end
     * @param earlier the chapter's pairs before the batch, oldest first
     */
    record Batch(List<Draft> cases, @Nullable Draft next, List<Pair> earlier) {

        /** Copies the lists. */
        Batch {
            cases = List.copyOf(cases);
            earlier = List.copyOf(earlier);
        }
    }

    static List<Batch> batches(final int size) {
        final List<Draft> drafts = PromptEvalCases.ALL.stream()
                .filter(Draft.class::isInstance)
                .map(Draft.class::cast)
                .toList();
        final List<Batch> batches = new ArrayList<>();
        for (int start = 0; start + size <= drafts.size(); start += size) {
            final boolean last = start + size >= drafts.size();
            batches.add(new Batch(
                    drafts.subList(start, start + size),
                    last ? null : drafts.get(start + size),
                    start == 0 ? List.of() : EARLIER_PAIRS));
        }
        return batches;
    }
}
