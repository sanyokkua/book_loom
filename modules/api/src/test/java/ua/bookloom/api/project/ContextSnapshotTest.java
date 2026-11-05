package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.PromptSection;

/** {@code ContextSnapshot} and {@code PromptFrame}: defensive copies, the no-call snapshot and a frame's hash. */
class ContextSnapshotTest {

    private static final PromptSection PREVIOUS =
            new PromptSection("previousPairs", "[Previous]", PromptSection.Origin.USER, List.of("Ніч була тиха."));

    @Test
    void constructor_listsMutatedAfterConstruction_doNotChangeTheSnapshot() {
        final List<PromptSection> sections = new ArrayList<>(List.of(PREVIOUS));
        final List<String> keys = new ArrayList<>(List.of("ch1:0"));
        final ContextSnapshot snapshot = new ContextSnapshot("ab12", sections, keys, false);

        sections.clear();
        keys.add("ch2:0");

        assertThat(snapshot.sections()).containsExactly(PREVIOUS);
        assertThat(snapshot.chapterSummaryKeys()).containsExactly("ch1:0");
    }

    @Test
    void noCall_holdsNoFrameNoSectionsAndIsNoRetry() {
        assertThat(ContextSnapshot.noCall()).isEqualTo(new ContextSnapshot("", List.of(), List.of(), false));
    }

    @Test
    void promptFrame_blankHash_isRejected() {
        assertThatThrownBy(() -> new PromptFrame(" ", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promptFrame_sectionsMutatedAfterConstruction_doNotChangeTheFrame() {
        final List<PromptSection> sections = new ArrayList<>(List.of(PREVIOUS));
        final PromptFrame frame = new PromptFrame("ab12", sections);

        sections.clear();

        assertThat(frame.sections()).containsExactly(PREVIOUS);
    }
}
