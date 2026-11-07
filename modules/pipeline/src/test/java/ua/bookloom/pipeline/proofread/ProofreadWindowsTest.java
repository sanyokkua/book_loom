package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
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

    private static Mark mark(final String segmentId) {
        return new Mark(segmentId, Mark.Tier.MARKED, List.of("flagged: echo"));
    }
}
