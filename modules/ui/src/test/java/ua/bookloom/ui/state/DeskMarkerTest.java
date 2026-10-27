package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;

/** The word and colour each review desk row carries. */
class DeskMarkerTest {

    private static SegmentView viewOf(final SegmentStatus status, final boolean kept) {
        return kept
                ? ReviewDeskFixtures.keptAsSource(1).getFirst()
                : ReviewFixtures.view("s-1", "ch1 · p01", status, List.of(), null, null);
    }

    // IF a row did not say what became of its segment, THEN the person could not tell what is done from what is left;
    // a kept-as-source row wins over everything and "later" wins over the status.
    @ParameterizedTest
    @CsvSource({
        "FLAGGED,false,false,TO_REVIEW",
        "ACCEPTED,false,false,ACCEPTED",
        "REVISED,false,false,YOUR_EDIT",
        "FLAGGED,true,false,LATER",
        "FLAGGED,false,true,KEPT",
        "ACCEPTED,true,true,KEPT"
    })
    void of_statusLaterAndKept_choosesTheMarker(
            final SegmentStatus status, final boolean later, final boolean kept, final DeskMarker expected) {
        assertThat(DeskMarker.of(viewOf(status, kept), later)).isEqualTo(expected);
    }
}
