package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ContextAssembled;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** The context a draft is announced with is the key to its frame and its call's own sections, in display text. */
class SegmentEventsContextTest {

    private static final ModelCalls NO_CALLS = (kind, segmentId, request) -> {
        throw new AssertionError("no model call expected");
    };

    private final List<JobEvent> emitted = new ArrayList<>();
    private final SegmentEvents events = new SegmentEvents(emitted::add, Map.of(), NO_CALLS);

    @Test
    void contextAssembled_sections_areForwardedInDisplayTextWithTheFrameKey() {
        final PromptSection items =
                new PromptSection("items", "Items:", PromptSection.Origin.USER, List.of("1: ⟦g0⟧Він⟦g1⟧ пішов."));
        final ContextSnapshot snapshot = new ContextSnapshot("f00d", List.of(items), List.of("ch1:0"), false);

        events.contextAssembled("Book.md:0", snapshot);

        assertThat(emitted).singleElement().isInstanceOfSatisfying(ContextAssembled.class, assembled -> {
            final ContextSnapshot shown = assembled.context();
            assertThat(shown.frameHash()).isEqualTo("f00d");
            assertThat(shown.chapterSummaryKeys()).containsExactly("ch1:0");
            assertThat(shown.sections()).singleElement().satisfies(section -> {
                assertThat(section.slot()).isEqualTo("items");
                assertThat(section.lines()).containsExactly("1: Він пішов.");
            });
        });
    }

    @Test
    void contextAssembled_noCall_announcesNoSections() {
        events.contextAssembled("Book.md:0", ContextSnapshot.noCall());

        assertThat(emitted)
                .singleElement()
                .isInstanceOfSatisfying(
                        ContextAssembled.class,
                        assembled -> assertThat(assembled.context()).isEqualTo(ContextSnapshot.noCall()));
    }
}
