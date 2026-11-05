package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.TmEntry;

/** The lines a draft is shown of what the memory offers: hints first, then suggestions, as display text. */
class TmLookupTest {

    private static TmEntry entry(final String source, final String target) {
        return new TmEntry("e:" + source, "p1", "hash", "ctx", source, target);
    }

    @Test
    void promptLines_hintsAndSuggestions_areLinesWithHintsFirstInDisplayText() {
        final TmLookup memory = new TmLookup(
                null,
                List.of(entry("He left the room.", "Він вийшов ⟦g0⟧із⟦g1⟧ кімнати.")),
                List.of(entry("He left the house.", "Він покинув будинок.")));

        assertThat(memory.promptLines())
                .containsExactly(
                        "He left the room. → Він вийшов із кімнати.", "He left the house. → Він покинув будинок.");
    }

    // A reuse is taken without a call, so no prompt ever shows it.
    @Test
    void promptLines_reuse_addsNoLine() {
        final TmLookup memory = new TmLookup(entry("He left the room.", "Він вийшов з кімнати."), List.of(), List.of());

        assertThat(memory.promptLines()).isEmpty();
    }

    @Test
    void promptLines_hintWhoseTargetIsBlankOnceItsTokensAreRemoved_isLeftOut() {
        final TmLookup memory = new TmLookup(null, List.of(entry("He left the room.", "⟦g0⟧⟦g1⟧")), List.of());

        assertThat(memory.promptLines()).isEmpty();
    }
}
