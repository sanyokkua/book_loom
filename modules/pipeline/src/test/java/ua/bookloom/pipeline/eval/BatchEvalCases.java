package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchContext.Pair;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * The batches of the A/B: consecutive runs of the fixed English → Ukrainian draft cases, so the number-, symbol- and
 * Roman-only lines, the drop cap, the footnote and the locked names fall into batches as they do in a book. A batch of
 * a size starts where the previous one ended; a size that does not divide the case list leaves its tail out.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchEvalCases {

    /** The batch sizes of the sweep; the same four the controller's bounds are chosen from. */
    static final List<Integer> SIZES = List.of(4, 8, 12, 16);

    private static final List<Pair> EARLIER_PAIRS =
            List.of(new Pair("It was late.", "Було пізно."), new Pair("He left.", "Він пішов."));

    /** One batch of the sweep: its items, the case behind each, and the read-only context it is sent with. */
    record Batch(List<Draft> cases, List<BatchItem> items, BatchContext context) {

        /** Copies the lists. */
        Batch {
            cases = List.copyOf(cases);
            items = List.copyOf(items);
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
            batches.add(batch(
                    drafts.subList(start, start + size),
                    start == 0 ? List.of() : EARLIER_PAIRS,
                    last ? null : drafts.get(start + size).masked()));
        }
        return batches;
    }

    private static Batch batch(final List<Draft> cases, final List<Pair> pairs, final String nextSource) {
        final List<BatchItem> items = new ArrayList<>();
        final List<String> glossary = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            final String id = String.valueOf(i + 1);
            final Draft draft = cases.get(i);
            items.add(new BatchItem(id, draft.masked()));
            draft.glossary().forEach(line -> glossary.add(line.startsWith("⟦g") ? id + ": " + line : line));
        }
        final String all =
                String.join("\n", items.stream().map(BatchItem::masked).toList());
        final List<String> present = glossary.stream()
                .filter(line -> Tokens.inOrder(line).size() > 0 || mentioned(line, all))
                .toList();
        return new Batch(
                cases,
                items,
                new BatchContext(new DraftContext(List.of(), null, present, List.of()), pairs, nextSource, List.of()));
    }

    /** A plain glossary line stays only when its term is in the batch, as the run's own context keeps it. */
    private static boolean mentioned(final String line, final String text) {
        final String term = line.contains(" → ") ? line.substring(0, line.indexOf(" → ")) : line;
        return Pattern.compile("\\b" + Pattern.quote(term) + "\\b")
                .matcher(text)
                .find();
    }
}
