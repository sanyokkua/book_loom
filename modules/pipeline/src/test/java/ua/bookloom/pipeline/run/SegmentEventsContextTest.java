package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ContextAssembled;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** The context a draft is announced with is the whole context it saw, in display text. */
class SegmentEventsContextTest {

    private static final ModelCalls NO_CALLS = (kind, segmentId, request) -> {
        throw new AssertionError("no model call expected");
    };

    private final List<JobEvent> emitted = new ArrayList<>();
    private final SegmentEvents events = new SegmentEvents(emitted::add, Map.of(), NO_CALLS);

    // The old rebuild dropped the recurring terms and the character sheet, so the screen showed less than the model
    // got.
    @Test
    void contextAssembled_snapshotWithLexiconAndCharacters_announcesBoth() {
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of("⟦g0⟧Він⟦g1⟧ пішов."),
                List.of(),
                List.of(),
                "A boy summons a djinni.",
                "Plain prose.",
                List.of(new SnapshotRendering("magician", "чарівник")),
                List.of("Nathaniel — male"));

        events.contextAssembled("Book.md:0", snapshot);

        assertThat(emitted).singleElement().isInstanceOfSatisfying(ContextAssembled.class, assembled -> {
            final ContextSnapshot shown = assembled.context();
            assertThat(shown.lexicon()).containsExactly(new SnapshotRendering("magician", "чарівник"));
            assertThat(shown.characters()).containsExactly("Nathaniel — male");
            assertThat(shown.precedingTargets()).containsExactly("Він пішов.");
            assertThat(shown.styleSheet()).isEqualTo("Plain prose.");
        });
    }
}
