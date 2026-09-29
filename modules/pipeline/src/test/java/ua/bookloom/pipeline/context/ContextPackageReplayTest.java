package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.DraftContext;

/** A retry rebuilds, from the snapshot's texts alone, the very context the first draft was shown. */
class ContextPackageReplayTest {

    private static final GlossaryEntry HALE_LOCKED =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
    private static final GlossaryEntry MILTON =
            ContextFixtures.entry("Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false);
    private static final GlossaryEntry MOREAU =
            ContextFixtures.entry("Moreau", null, TermType.CHARACTER, Gender.MALE, false);
    private static final String SOURCE = "Hale met Moreau in Milton.";

    @Test
    void replay_assembledSnapshot_rebuildsTheSameDraftContext() {
        // preceding targets, every term line (a locked one by its token), memory lines and summary come back unchanged
        final Chunk chunk = ContextFixtures.chunk("Він прийшов.", SOURCE);
        final Segment segment = chunk.segments().get(1);
        final List<GlossaryEntry> glossary = List.of(HALE_LOCKED, MILTON, MOREAU);
        final TmLookup memory = new TmLookup(
                ContextFixtures.tmEntry(SOURCE, "Гейл зустрів Моро в Мілтоні."),
                List.of(ContextFixtures.tmEntry("Hale met Moreau.", "Гейл зустрів Моро.")),
                List.of(ContextFixtures.tmEntry("Hale met Milton.", "Гейл зустрів Мілтона.")));
        final ContextPackage assembled = ContextPackageAssembler.assemble(
                chunk,
                segment,
                ContextFixtures.mask(segment, glossary),
                memory,
                ContextFixtures.inputs("Гейл повернувся.", 2, glossary, List.of("Він прийшов.")));
        final ContextSnapshot snapshot = assembled.snapshot();

        final DraftContext replayed = ContextPackageAssembler.replay(snapshot, ContextFixtures.mask(segment, glossary));

        assertThat(replayed).isEqualTo(assembled.draftContext());
        assertThat(replayed.glossaryLines())
                .containsExactly(
                        "⟦g0⟧ → Гейл, character, male", "Milton → Мілтон (place, unknown)", "Moreau (character, male)");
        assertThat(replayed.memoryLines())
                .containsExactly("Hale met Moreau. → Гейл зустрів Моро.", "Hale met Milton. → Гейл зустрів Мілтона.");
        assertThat(replayed.precedingTargets()).containsExactly("Він прийшов.");
        assertThat(replayed.summary()).isEqualTo("Гейл повернувся.");
    }

    @Test
    void replay_termsChangedSinceTheDraft_showsTheSnapshotsRenderings() {
        // the snapshot is what was shown; today's glossary is never read
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of(),
                List.of(new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, false)),
                List.of(),
                null,
                "Neutral register.");
        final Segment segment = ContextFixtures.segment(0, SOURCE);

        final DraftContext replayed =
                ContextPackageAssembler.replay(snapshot, ContextFixtures.mask(segment, List.of()));

        assertThat(replayed.glossaryLines()).containsExactly("Hale → Хейл (character, male)");
        assertThat(replayed.summary()).isNull();
    }
}
