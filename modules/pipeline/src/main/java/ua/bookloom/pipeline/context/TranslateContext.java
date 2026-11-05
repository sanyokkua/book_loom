package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.PromptHygiene;

/**
 * What a translate-family call is shown around its items, fixed when the draft is made (design D8): the window's
 * previous pairs and next source, the book map as it stood, the memory's lines and the items' masks. A repair or a
 * re-ask of the same items plans from the same context, so it sees the facts its draft saw.
 *
 * @param window the window the draft was planned with; its focus is the draft's items
 * @param map the book map as it stood when the draft was made
 * @param memory the translation memory's lines for the items; copied, blank, placeholder and repeated lines left out
 * @param masks the mask of each item by segment id; copied
 * @param keyTerms the recurring key terms the draft asked renderings for; copied, empty when not asked
 * @param summaries the earlier chapters' summaries read when the draft was made, oldest first, so its repair and
 *     re-ask carry the same; copied, empty for none
 */
public record TranslateContext(
        Window window,
        BookMap map,
        List<String> memory,
        Map<String, ProtectedMask> masks,
        List<String> keyTerms,
        List<EarlierSummary> summaries) {

    /** Copies the parts. */
    public TranslateContext {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(map, "map");
        memory = PromptHygiene.clean(Objects.requireNonNull(memory, "memory"));
        masks = Map.copyOf(Objects.requireNonNull(masks, "masks"));
        keyTerms = List.copyOf(Objects.requireNonNull(keyTerms, "keyTerms"));
        summaries = List.copyOf(Objects.requireNonNull(summaries, "summaries"));
    }

    /**
     * The same neighbours around other items: a subset of the batch, one item's repair or a piece of a segment.
     *
     * @param focus the non-null items the call works on
     * @return the window with the previous pairs and the next source kept; never null
     */
    public Window windowFor(final Focus focus) {
        return new Window(window.previous(), Objects.requireNonNull(focus, "focus"), window.nextSource());
    }

    /**
     * The masks of the given segments in their order, a segment with no mask left out.
     *
     * @param segmentIds the non-null segment ids
     * @return never null
     */
    public List<ProtectedMask> masksOf(final List<String> segmentIds) {
        return segmentIds.stream().filter(masks::containsKey).map(masks::get).toList();
    }
}
