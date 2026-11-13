package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.Tokens;

/**
 * The text one translate-family call sees (design D8): the segments just before its focus, the focus, and the next
 * segment's source. The map lines of the call are chosen from this text alone, so nothing from elsewhere in the book
 * reaches the model.
 *
 * @param previous the segments before the focus in reading order; copied, empty at the body's start
 * @param focus the items the call works on
 * @param nextSource the masked source of the segment after the focus, or null at the body's end
 * @param nextTarget the decided text of the segment after the focus, shown read-only to a proofread call, or null for a
 *     call that drafts
 */
public record Window(
        List<WindowPair> previous,
        Focus focus,
        @Nullable String nextSource,
        @Nullable String nextTarget) {

    /** Copies the previous pairs. */
    public Window {
        previous = List.copyOf(Objects.requireNonNull(previous, "previous"));
        Objects.requireNonNull(focus, "focus");
    }

    /**
     * A window of a call that drafts: the next segment's source alone.
     *
     * @param previous the non-null pairs before the focus
     * @param focus the non-null items
     * @param nextSource the next source, or null
     */
    public Window(final List<WindowPair> previous, final Focus focus, @Nullable final String nextSource) {
        this(previous, focus, nextSource, null);
    }

    /**
     * A window of the focus alone, for a call built with no neighbours.
     *
     * @param focus the non-null items
     * @return the window with no previous pair and no next source
     */
    public static Window of(final Focus focus) {
        return new Window(List.of(), focus, null);
    }

    /**
     * The source text the window holds, with every token turned to a space so a name next to one is still found.
     *
     * @return the previous sources, the focus sources and the next source, in reading order; never null
     */
    public List<String> visibleSources() {
        final Stream<String> next = nextSource == null ? Stream.empty() : Stream.of(nextSource);
        return Stream.of(
                        previous.stream().map(WindowPair::maskedSource),
                        focus.items().stream().map(FocusItem::maskedSource),
                        next)
                .flatMap(texts -> texts)
                .map(text -> Tokens.replace(text, " "))
                .toList();
    }
}
