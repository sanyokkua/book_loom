package ua.bookloom.pipeline.batch;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.PromptHygiene;

/**
 * What a batch prompt carries besides its items, all read-only: the run's draft context (summary, glossary, memory,
 * suggestions), the last few source and target pairs of the chapter, the source of the item after the batch, whose
 * pronouns and gender the last item may depend on, and who is who among the characters present.
 *
 * @param draft the summary, glossary, memory and suggestion parts; its preceding targets are not used
 * @param precedingPairs the chapter's last decided pairs, oldest first; two or three
 * @param nextSource the masked source of the segment after the batch, or null at the end of the unit
 * @param characters one line per character present in the batch, with the gender the glossary knows
 */
public record BatchContext(
        DraftContext draft,
        List<Pair> precedingPairs,
        @Nullable String nextSource,
        List<String> characters) {

    /** One decided segment shown as context. */
    public record Pair(String source, String target) {

        /** Rejects missing text. */
        public Pair {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    /** Rejects missing parts and drops filler and repeated character lines. */
    public BatchContext {
        Objects.requireNonNull(draft, "draft");
        precedingPairs = List.copyOf(Objects.requireNonNull(precedingPairs, "precedingPairs"));
        nextSource = PromptHygiene.cleanText(nextSource);
        characters = PromptHygiene.clean(Objects.requireNonNull(characters, "characters"));
    }

    /** A context with nothing beyond the items. */
    public static BatchContext empty() {
        return new BatchContext(DraftContext.empty(), List.of(), null, List.of());
    }
}
