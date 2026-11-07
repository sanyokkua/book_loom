package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The windows of a mark list (design D15 "Windows"): marks merged, the focus capped, one neighbour on each side. */
class ProofreadWindowsTest {

    private static final List<String> CH02 =
            IntStream.range(0, 18).mapToObj(index -> "ch02.md:" + index).toList();
    private static final List<String> CH06 =
            IntStream.range(0, 30).mapToObj(index -> "ch06.xhtml:" + index).toList();

    @Test
    void of_oneMark_oneWindowWithNeighbours() {
        final List<ProofreadWindow> windows = ProofreadWindows.of(List.of(mark("ch02.md:14")), CH02, id -> true, 4);

        assertThat(windows)
                .extracting(ProofreadWindow::focusIds, ProofreadWindow::beforeId, ProofreadWindow::afterId)
                .containsExactly(tuple(List.of("ch02.md:14"), "ch02.md:13", "ch02.md:15"));
    }

    @Test
    void of_closeMarks_oneWindowHoldsThemAndTheLineBetween() {
        final List<ProofreadWindow> windows = ProofreadWindows.of(
                List.of(mark("ch06.xhtml:20"), mark("ch06.xhtml:21"), mark("ch06.xhtml:23")), CH06, id -> true, 4);

        assertThat(windows).hasSize(1);
        assertThat(windows.getFirst().focusIds())
                .containsExactly("ch06.xhtml:20", "ch06.xhtml:21", "ch06.xhtml:22", "ch06.xhtml:23");
        assertThat(windows.getFirst().reasons().get("ch06.xhtml:22")).isEmpty();
        assertThat(windows.getFirst().beforeId()).isEqualTo("ch06.xhtml:19");
        assertThat(windows.getFirst().afterId()).isEqualTo("ch06.xhtml:24");
    }

    @Test
    void of_marksTwoLinesApart_twoWindows() {
        final List<ProofreadWindow> windows =
                ProofreadWindows.of(List.of(mark("ch06.xhtml:20"), mark("ch06.xhtml:23")), CH06, id -> true, 4);

        assertThat(windows)
                .extracting(ProofreadWindow::focusIds)
                .containsExactly(List.of("ch06.xhtml:20"), List.of("ch06.xhtml:23"));
    }

    @Test
    void of_fiveMarksInARowAtBatchSizeFour_twoWindows() {
        final List<Mark> marks = IntStream.range(20, 25)
                .mapToObj(index -> mark("ch06.xhtml:" + index))
                .toList();

        final List<ProofreadWindow> windows = ProofreadWindows.of(marks, CH06, id -> true, 4);

        assertThat(windows).extracting(window -> window.focusIds().size()).containsExactly(4, 1);
    }

    // A neighbour or a line between that holds no decided text is never shown.
    @Test
    void of_unreadableNeighbourAndGap_leftOut() {
        final Set<String> unread = Set.of("ch06.xhtml:19", "ch06.xhtml:21");

        final List<ProofreadWindow> windows = ProofreadWindows.of(
                List.of(mark("ch06.xhtml:20"), mark("ch06.xhtml:22")), CH06, id -> !unread.contains(id), 4);

        assertThat(windows)
                .extracting(ProofreadWindow::focusIds, ProofreadWindow::beforeId)
                .containsExactly(tuple(List.of("ch06.xhtml:20"), null), tuple(List.of("ch06.xhtml:22"), null));
    }

    // Max's whole-book round: 3,000 story paragraphs at batch size 4 are 750 windows of 4 consecutive paragraphs.
    @Test
    void tiles_3000ParagraphsBatchFour_750Windows() {
        final List<String> book = IntStream.range(0, 3000)
                .mapToObj(index -> "ch05.xhtml:" + index)
                .toList();

        final List<ProofreadWindow> windows = ProofreadWindows.tiles(book, id -> true, 4, Map.of());

        assertThat(windows).hasSize(750);
        assertThat(windows.get(10))
                .extracting(ProofreadWindow::focusIds, ProofreadWindow::beforeId, ProofreadWindow::afterId)
                .containsExactly(
                        List.of("ch05.xhtml:40", "ch05.xhtml:41", "ch05.xhtml:42", "ch05.xhtml:43"),
                        "ch05.xhtml:39",
                        "ch05.xhtml:44");
        assertThat(windows.getFirst().beforeId()).isNull();
        assertThat(windows.getLast().afterId()).isNull();
    }

    // A segment with no decided text is skipped, and a mark still open rides along as its segment's reasons.
    @Test
    void tiles_unreadableSkippedAndOpenMarkRides() {
        final List<ProofreadWindow> windows = ProofreadWindows.tiles(
                CH02.subList(0, 6), id -> !"ch02.md:2".equals(id), 2, Map.of("ch02.md:4", List.of("flagged: echo")));

        assertThat(windows)
                .extracting(ProofreadWindow::focusIds)
                .containsExactly(
                        List.of("ch02.md:0", "ch02.md:1"), List.of("ch02.md:3", "ch02.md:4"), List.of("ch02.md:5"));
        assertThat(windows.get(1).beforeId()).isEqualTo("ch02.md:1");
        assertThat(windows.get(1).reasons())
                .containsEntry("ch02.md:4", List.of("flagged: echo"))
                .containsEntry("ch02.md:3", List.of());
    }

    private static Mark mark(final String segmentId) {
        return new Mark(segmentId, Mark.Tier.MARKED, List.of("flagged: echo"));
    }
}
