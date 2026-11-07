package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Groups the mark list into proofread windows (design D15 "Windows"): marks in reading order with at most one unmarked
 * story segment between them share a window, the segment between them read too, up to the model profile's batch size;
 * each window gets the decided segment before and after it as read-only neighbours. The context builder then fits the
 * window to the model tier's budgets, so a window is never sized here by tokens.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProofreadWindows {

    /** The most unmarked segments between two marks that still share a window. */
    private static final int MAX_GAP = 1;

    /**
     * The windows of a mark list.
     *
     * @param marks the non-null marks in reading order
     * @param order the non-null story segment ids in reading order
     * @param readable which segments hold a decided text a window may read
     * @param batchSize the most focus segments of one window; at least 1
     * @return the windows in reading order; never null, empty when there is no mark
     */
    public static List<ProofreadWindow> of(
            final List<Mark> marks, final List<String> order, final Predicate<String> readable, final int batchSize) {
        Objects.requireNonNull(marks, "marks");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(readable, "readable");
        final int size = Math.max(1, batchSize);
        final Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < order.size(); index++) {
            positions.put(order.get(index), index);
        }
        final List<ProofreadWindow> windows = grouped(marks, new Story(order, readable, positions), size);
        log.debug("Proofread windows marks={} windows={} batchSize={}", marks.size(), windows.size(), size);
        return List.copyOf(windows);
    }

    /**
     * The windows of Max's whole-book round (design D15 "Windows"): every readable story segment in reading order, in
     * consecutive windows of at most the batch size, each with the readable segment before and after it as read-only
     * neighbours; a mark still open rides along as its segment's reasons.
     *
     * @param order the non-null story segment ids in reading order
     * @param readable which segments hold a decided text a window may read
     * @param batchSize the most focus segments of one window; at least 1
     * @param reasons the non-null reasons of the segments still marked, by segment id
     * @return the windows in reading order; never null, empty when no segment is readable
     */
    public static List<ProofreadWindow> tiles(
            final List<String> order,
            final Predicate<String> readable,
            final int batchSize,
            final Map<String, List<String>> reasons) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(readable, "readable");
        Objects.requireNonNull(reasons, "reasons");
        final int size = Math.max(1, batchSize);
        final List<String> listed = order.stream().filter(readable).toList();
        final List<ProofreadWindow> windows = new ArrayList<>();
        for (int first = 0; first < listed.size(); first += size) {
            final int end = Math.min(first + size, listed.size());
            final Map<String, List<String>> focus = new LinkedHashMap<>();
            listed.subList(first, end).forEach(id -> focus.put(id, reasons.getOrDefault(id, List.of())));
            windows.add(new ProofreadWindow(
                    List.copyOf(focus.keySet()),
                    focus,
                    first == 0 ? null : listed.get(first - 1),
                    end == listed.size() ? null : listed.get(end)));
        }
        log.debug(
                "Proofread whole-book windows readable={} windows={} batchSize={} marked={}",
                listed.size(),
                windows.size(),
                size,
                reasons.size());
        return List.copyOf(windows);
    }

    /** The story's reading order, which segments a window may read, and each segment's position. */
    private record Story(List<String> order, Predicate<String> readable, Map<String, Integer> positions) {}

    private static List<ProofreadWindow> grouped(final List<Mark> marks, final Story story, final int size) {
        final List<ProofreadWindow> windows = new ArrayList<>();
        Map<String, List<String>> open = new LinkedHashMap<>();
        int last = -1;
        for (final Mark mark : marks) {
            final Integer at = story.positions().get(mark.segmentId());
            if (at == null) {
                log.debug("Mark segmentId={} is no story segment; not read", mark.segmentId());
                continue;
            }
            if (!open.isEmpty() && !joins(story.order(), story.readable(), last, at, open.size(), size)) {
                windows.add(closed(open, story.order(), story.readable(), story.positions()));
                open = new LinkedHashMap<>();
            }
            for (int between = open.isEmpty() ? at : last + 1; between < at; between++) {
                open.put(story.order().get(between), List.of());
            }
            open.put(mark.segmentId(), mark.reasons());
            last = at;
        }
        if (!open.isEmpty()) {
            windows.add(closed(open, story.order(), story.readable(), story.positions()));
        }
        return windows;
    }

    // A mark joins the open window when at most one readable segment lies between, and the focus stays within size.
    private static boolean joins(
            final List<String> order,
            final Predicate<String> readable,
            final int last,
            final int at,
            final int held,
            final int size) {
        final int gap = at - last - 1;
        final boolean gapReadable = order.subList(last + 1, at).stream().allMatch(readable);
        return gap <= MAX_GAP && gapReadable && held + gap + 1 <= size;
    }

    private static ProofreadWindow closed(
            final Map<String, List<String>> focus,
            final List<String> order,
            final Predicate<String> readable,
            final Map<String, Integer> positions) {
        final List<String> ids = List.copyOf(focus.keySet());
        final int first = Objects.requireNonNull(positions.get(ids.getFirst()), "first");
        final int end = Objects.requireNonNull(positions.get(ids.getLast()), "last");
        final ProofreadWindow window = new ProofreadWindow(
                ids, focus, neighbour(order, readable, first - 1), neighbour(order, readable, end + 1));
        log.debug(
                "Proofread window {} focus={} before={} after={}",
                window.label(),
                ids.size(),
                window.beforeId(),
                window.afterId());
        return window;
    }

    private static @Nullable String neighbour(
            final List<String> order, final Predicate<String> readable, final int index) {
        if (index < 0 || index >= order.size()) {
            return null;
        }
        final String id = order.get(index);
        return readable.test(id) ? id : null;
    }
}
