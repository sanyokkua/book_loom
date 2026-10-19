package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.SnapshotTmHit.TmHitKind;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.memory.TmLookup;

/** How translation-memory hints, suggestions and a reuse reach the prompt and the snapshot. */
class ContextPackageMemoryTest {

    private static ContextPackage assemble(final TmLookup memory) {
        final Chunk chunk = ContextFixtures.chunk("He left the room.");
        final Segment segment = chunk.segments().getFirst();
        final ContextInputs inputs = ContextFixtures.inputs(null, 0, List.of(), List.of());
        return ContextPackageAssembler.assemble(
                chunk, segment, ContextFixtures.mask(segment, List.of()), memory, inputs);
    }

    @Test
    void assemble_hintsAndSuggestions_areLinesWithHintsFirstInDisplayText() {
        final TmLookup memory = new TmLookup(
                null,
                List.of(ContextFixtures.tmEntry("He left the room.", "Він вийшов ⟦g0⟧із⟦g1⟧ кімнати.")),
                List.of(ContextFixtures.tmEntry("He left the house.", "Він покинув будинок.")));

        final ContextPackage pack = assemble(memory);

        assertThat(pack.draftContext().memoryLines())
                .containsExactly(
                        "He left the room. → Він вийшов із кімнати.", "He left the house. → Він покинув будинок.");
        assertThat(pack.snapshot().tmHits())
                .containsExactly(
                        new SnapshotTmHit(TmHitKind.EXACT, "He left the room.", "Він вийшов із кімнати."),
                        new SnapshotTmHit(TmHitKind.FUZZY, "He left the house.", "Він покинув будинок."));
    }

    @Test
    void assemble_reuse_isRecordedAsAContextHitAndAddsNoLine() {
        final TmLookup memory = new TmLookup(
                ContextFixtures.tmEntry("He left the room.", "Він вийшов з кімнати."), List.of(), List.of());

        final ContextPackage pack = assemble(memory);

        assertThat(pack.draftContext().memoryLines()).isEmpty();
        assertThat(pack.snapshot().tmHits())
                .containsExactly(new SnapshotTmHit(TmHitKind.CONTEXT, "He left the room.", "Він вийшов з кімнати."));
    }

    @Test
    void assemble_noMemory_addsNoLineAndNoHit() {
        final ContextPackage pack = assemble(ContextFixtures.NO_MEMORY);

        assertThat(pack.draftContext().memoryLines()).isEmpty();
        assertThat(pack.snapshot().tmHits()).isEmpty();
    }

    @Test
    void assemble_hintWhoseTargetIsBlankAfterMasking_isLeftOut() {
        final TmLookup memory =
                new TmLookup(null, List.of(ContextFixtures.tmEntry("He left the room.", "⟦g0⟧⟦g1⟧")), List.of());

        assertThat(assemble(memory).draftContext().memoryLines()).isEmpty();
    }
}
