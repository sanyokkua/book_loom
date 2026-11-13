package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The written segment list of arm 2 (redesign task 2.2) is the one the task describes, fixed before the run. */
class ArmSegmentsTest {

    // The task fixes the size at exactly sixty, each id named once.
    @Test
    void segments_armTwoList_holdsSixtyDistinctIds() {
        final List<String> ids = ArmSegments.load(ArmSegments.ARM_TWO).ids();

        assertThat(ids).hasSize(60).doesNotHaveDuplicates();
    }

    // Every block of both mini books (each needs a model call) and then the first ten paragraphs of chapter 1.
    @Test
    void segments_armTwoList_holdsBothMiniBooksThenTheSequenceHead() {
        final List<String> ids = ArmSegments.load(ArmSegments.ARM_TWO).ids();

        assertThat(ids.subList(0, 33))
                .containsExactlyElementsOf(MiniBook.load(MiniBook.MINI_EN).blocks().stream()
                        .map(block -> "mini-en/" + block.id())
                        .toList());
        assertThat(ids.subList(33, 50))
                .containsExactlyElementsOf(MiniBook.load(MiniBook.MINI_RU).blocks().stream()
                        .map(block -> "mini-ru/" + block.id())
                        .toList());
        assertThat(ids.subList(50, 60))
                .containsExactly(
                        "sequence/book.md:1",
                        "sequence/book.md:2",
                        "sequence/book.md:3",
                        "sequence/book.md:4",
                        "sequence/book.md:5",
                        "sequence/book.md:6",
                        "sequence/book.md:7",
                        "sequence/book.md:8",
                        "sequence/book.md:9",
                        "sequence/book.md:10");
    }

    // The sequence head the run imports ends with the tenth paragraph, so no later paragraph is drafted.
    @Test
    void sequenceHead_tenParagraphs_endsAtLineTwentyFour() {
        final String head = ArmSegments.sequenceHead(SequenceFixture.bookText(), 10);

        assertThat(head.lines().toList()).hasSize(24).startsWith("# The Courtyard");
        assertThat(head.lines().toList().getLast()).startsWith("“The pentacle was perfect");
    }
}
