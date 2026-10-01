package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;

/** Which segment the review panel shows after each action. */
class ReviewNextTest {

    private static final List<String> SHOWN = List.of("a", "b", "c");

    private static SegmentView listed(final String id) {
        return ReviewFixtures.view(id, "p-" + id, SegmentStatus.FLAGGED, List.of(), null, null);
    }

    private static List<SegmentView> listedOf(final String ids) {
        return "-".equals(ids)
                ? List.of()
                : List.of(ids.split(" ")).stream().map(ReviewNextTest::listed).toList();
    }

    // IF a decided segment stayed on show, THEN the person would keep reading a segment that has left the list; a
    // decision moves on to the next one after it, wraps to the first when it was last, and leaves nothing when none is
    // left; Skip follows the desk; Revert and Retry stay.
    @ParameterizedTest
    @CsvSource({
        "accept,b,b,a c,c",
        "saveEdit,b,b,a c,c",
        "accept,c,c,a b,a",
        "accept,a,a,-,",
        "accept,b,b,a b c,c",
        "skip,a,c,a b c,c",
        "revert,b,b,a b c,b",
        "retry,b,b,a c,b"
    })
    void after_action_choosesTheSegmentToShow(
            final String action,
            final String decided,
            final String deskNext,
            final String listed,
            final @Nullable String expected) {
        assertThat(ReviewNext.after(action, decided, deskNext, SHOWN, listedOf(listed)))
                .isEqualTo(expected);
    }
}
