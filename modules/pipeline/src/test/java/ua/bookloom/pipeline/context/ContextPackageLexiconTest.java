package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.prompt.DraftContext;

/** The established renderings of recurring terms: which are shown, within their own share, and what is recorded. */
class ContextPackageLexiconTest {

    private static final GlossaryEntry HALE =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
    private static final LexiconEntry MASTER = LexiconEntry.of("p1", "master").seen("господар");

    private static ContextPackage assemble(
            final String text,
            final List<GlossaryEntry> glossary,
            final List<LexiconEntry> lexicon,
            final int allowance) {
        final Chunk chunk = ContextFixtures.chunk(text);
        final Segment segment = chunk.segments().getFirst();
        final ContextInputs inputs = new ContextInputs(
                ContextFixtures.STYLE, "Hale searches.", 2, glossary, List.of("Один.", "Два."), allowance, lexicon);
        return ContextPackageAssembler.assemble(
                chunk, segment, ContextFixtures.mask(segment, glossary), ContextFixtures.NO_MEMORY, inputs);
    }

    @Test
    void assemble_termTheChunkNames_showsItsEstablishedRendering() {
        final ContextPackage assembled = assemble("The master left.", List.of(), List.of(MASTER), 1000);

        assertThat(assembled.draftContext().lexiconLines()).containsExactly("master → господар");
        assertThat(assembled.snapshot().lexicon()).containsExactly(new SnapshotRendering("master", "господар"));
    }

    @Test
    void assemble_termTheChunkDoesNotName_isLeftOut() {
        final ContextPackage assembled = assemble("The imp left.", List.of(), List.of(MASTER), 1000);

        assertThat(assembled.draftContext().lexiconLines()).isEmpty();
        assertThat(assembled.snapshot().lexicon()).isEmpty();
    }

    @Test
    void assemble_termWithNoRenderingYet_isLeftOut() {
        final ContextPackage assembled =
                assemble("The master left.", List.of(), List.of(LexiconEntry.of("p1", "master")), 1000);

        assertThat(assembled.draftContext().lexiconLines()).isEmpty();
    }

    @Test
    void assemble_termTheGlossaryHolds_isLeftToTheGlossary() {
        final GlossaryEntry master = ContextFixtures.entry("Master", "Майстер", TermType.TERM, Gender.UNKNOWN, false);

        final ContextPackage assembled = assemble("The Master left.", List.of(master), List.of(MASTER), 1000);

        assertThat(assembled.draftContext().lexiconLines()).isEmpty();
        assertThat(assembled.draftContext().glossaryLines()).containsExactly("Master → Майстер (term, unknown)");
    }

    // The share is a fifth of what the glossary leaves; the lines that do not fit are cut, the rest keep their room.
    @Test
    void assemble_longLexicon_isCutToItsOwnShareAndLeavesRoomForTheRest() {
        final List<LexiconEntry> many = IntStream.range(0, 12)
                .mapToObj(index -> LexiconEntry.of("p1", "term" + index).seen("термін" + index))
                .toList();
        final String text =
                "Hale " + String.join(" ", many.stream().map(LexiconEntry::term).toList());

        final DraftContext context = assemble(text, List.of(HALE), many, 100).draftContext();

        assertThat(context.lexiconLines()).isNotEmpty().hasSizeLessThan(many.size());
        assertThat(context.glossaryLines()).hasSize(1);
        assertThat(context.precedingTargets()).containsExactly("Один.", "Два.");
        assertThat(context.summary()).isEqualTo("Hale searches.");
    }

    @Test
    void assemble_allowanceHoldsOnlyTheGlossary_neverTakesTheGlossaryRoomForTheLexicon() {
        final ContextPackage assembled = assemble("Hale and the master.", List.of(HALE), List.of(MASTER), 12);

        assertThat(assembled.draftContext().glossaryLines()).hasSize(1);
        assertThat(assembled.draftContext().lexiconLines()).isEmpty();
    }

    @Test
    void replay_snapshotWithRenderings_rebuildsTheSameLexiconLines() {
        final ContextPackage assembled = assemble("The master left.", List.of(), List.of(MASTER), 1000);
        final Segment segment = ContextFixtures.segment(0, "The master left.");

        final DraftContext replayed =
                ContextPackageAssembler.replay(assembled.snapshot(), ContextFixtures.mask(segment, List.of()), segment);

        assertThat(replayed).isEqualTo(assembled.draftContext());
        assertThat(replayed.lexiconLines()).containsExactly("master → господар");
    }
}
