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
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadForecast;

/**
 * Groups the mark list into proofread windows (design D15 "Windows"): marks in reading order with at most one unmarked
 * story segment between them share a window, the segment between them read too, up to the model profile's batch size;
 * each window gets the decided segment before and after it as read-only neighbours. A machine text the person reviewed
 * or whose rewrite they undid is never in a window's focus, only a neighbour (the owner's rule: the person's decision
 * is not rewritten silently). The context builder then fits the window to the model tier's budgets, so a window is never sized here by
 * tokens.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProofreadWindows {

    /** The most unmarked segments between two marks that still share a window. */
    private static final int MAX_GAP = 1;

    private static final String FOCUSABLE = "focusable";

    /**
     * The windows of a mark list.
     *
     * @param marks the non-null marks in reading order
     * @param order the non-null story segment ids in reading order
     * @param readable which segments hold a decided text a window may show
     * @param focusable which readable segments a window may also rewrite ({@link MarkList#isFocusable})
     * @param batchSize the most focus segments of one window; at least 1
     * @return the windows in reading order; never null, empty when there is no mark
     */
    public static List<ProofreadWindow> of(
            final List<Mark> marks,
            final List<String> order,
            final Predicate<String> readable,
            final Predicate<String> focusable,
            final int batchSize) {
        Objects.requireNonNull(marks, "marks");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(readable, "readable");
        Objects.requireNonNull(focusable, FOCUSABLE);
        final int size = Math.max(1, batchSize);
        final Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < order.size(); index++) {
            positions.put(order.get(index), index);
        }
        final List<ProofreadWindow> windows = grouped(marks, new Story(order, readable, focusable, positions), size);
        log.debug("Proofread windows marks={} windows={} batchSize={}", marks.size(), windows.size(), size);
        return List.copyOf(windows);
    }

    /**
     * The windows of Max's whole-book round (design D15 "Windows"): every focusable story segment in reading order, in
     * windows of at most the batch size that a segment the person reviewed ends, each with the readable segment before
     * and after it as read-only neighbours; a mark still open rides along as its segment's reasons.
     *
     * @param order the non-null story segment ids in reading order
     * @param readable which segments hold a decided text a window may show
     * @param focusable which readable segments a window may also rewrite ({@link MarkList#isFocusable})
     * @param batchSize the most focus segments of one window; at least 1
     * @param reasons the non-null reasons of the segments still marked, by segment id
     * @return the windows in reading order; never null, empty when no segment is focusable
     */
    public static List<ProofreadWindow> tiles(
            final List<String> order,
            final Predicate<String> readable,
            final Predicate<String> focusable,
            final int batchSize,
            final Map<String, List<String>> reasons) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(readable, "readable");
        Objects.requireNonNull(focusable, FOCUSABLE);
        Objects.requireNonNull(reasons, "reasons");
        final int size = Math.max(1, batchSize);
        final List<String> listed = order.stream().filter(readable).toList();
        final List<ProofreadWindow> windows = new ArrayList<>();
        int first = 0;
        while (first < listed.size()) {
            final int end = focusEnd(listed, first, size, focusable);
            if (end > first) {
                windows.add(tile(listed, first, end, reasons));
            }
            first = Math.max(end, first + 1);
        }
        log.debug(
                "Proofread whole-book windows readable={} windows={} batchSize={} marked={}",
                listed.size(),
                windows.size(),
                size,
                reasons.size());
        return List.copyOf(windows);
    }

    // The end of the run of focusable segments from first, at most size long; first itself when it is not focusable.
    private static int focusEnd(
            final List<String> listed, final int first, final int size, final Predicate<String> focusable) {
        int end = first;
        while (end < listed.size() && end - first < size && focusable.test(listed.get(end))) {
            end++;
        }
        return end;
    }

    private static ProofreadWindow tile(
            final List<String> listed, final int first, final int end, final Map<String, List<String>> reasons) {
        final Map<String, List<String>> focus = new LinkedHashMap<>();
        listed.subList(first, end).forEach(id -> focus.put(id, reasons.getOrDefault(id, List.of())));
        return new ProofreadWindow(
                List.copyOf(focus.keySet()),
                focus,
                first == 0 ? null : listed.get(first - 1),
                end == listed.size() ? null : listed.get(end));
    }

    /**
     * The Proofread stage's forecast at Start (design D15 "Estimate"): Max's whole-book round as the story paragraphs
     * over the batch size, rounded up; the marked windows are counted by the window as the marks arrive.
     *
     * @param document the non-null opened book
     * @param coverage the non-null coverage of the run's quality
     * @param batchSize the most focus segments of one window; at least 1
     * @return the forecast; never null
     */
    public static ProofreadForecast forecast(
            final Document document, final ProofreadCoverage coverage, final int batchSize) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(coverage, "coverage");
        final int story = MarkList.storySegments(document).size();
        final int windows = coverage == ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH
                ? Math.ceilDiv(story, Math.max(1, batchSize))
                : 0;
        log.debug(
                "Proofread forecast coverage={} storyParagraphs={} batchSize={} wholeBookWindows={}",
                coverage,
                story,
                batchSize,
                windows);
        return new ProofreadForecast(coverage, windows);
    }

    /** The story's reading order, which segments a window may show and rewrite, and each segment's position. */
    private record Story(
            List<String> order,
            Predicate<String> readable,
            Predicate<String> focusable,
            Map<String, Integer> positions) {}

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
            if (!open.isEmpty() && !joins(story.order(), story.focusable(), last, at, open.size(), size)) {
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

    // A mark joins the open window when at most one focusable segment lies between, and the focus stays within size.
    private static boolean joins(
            final List<String> order,
            final Predicate<String> focusable,
            final int last,
            final int at,
            final int held,
            final int size) {
        final int gap = at - last - 1;
        final boolean gapFocusable = order.subList(last + 1, at).stream().allMatch(focusable);
        return gap <= MAX_GAP && gapFocusable && held + gap + 1 <= size;
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
