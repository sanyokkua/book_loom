package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.batch.BatchContexts;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.prompt.DraftContext;

/** A prompt lists only what the text being translated names: not the chunk's names, not the book's. */
class ContextScopeTest {

    private static final int ALLOWANCE = 120;

    private static GlossaryEntry name(final String term, final String target) {
        return ContextFixtures.entry(term, target, TermType.PLACE, Gender.UNKNOWN, false);
    }

    private static final List<GlossaryEntry> SIX = List.of(
            name("Corvin", "Корвін"),
            name("Dunmere", "Данмір"),
            name("Eastgate", "Істгейт"),
            name("Fallow", "Феллоу"),
            name("Gantry", "Гентрі"),
            name("Harrow", "Герроу"));

    private static ContextPackage assemble(final Chunk chunk, final int index, final List<GlossaryEntry> glossary) {
        final Segment segment = chunk.segments().get(index);
        return ContextPackageAssembler.assemble(
                chunk,
                segment,
                ContextFixtures.mask(segment, glossary),
                ContextFixtures.NO_MEMORY,
                ContextFixtures.inputs(null, 0, glossary, List.of()));
    }

    @Test
    void assemble_sixNamesInTheGlossaryOneInTheParagraph_holdsExactlyThatOne() {
        final Chunk chunk = ContextFixtures.chunk(
                "Corvin and Dunmere argued.", "The wind rose over Harrow.", "Gantry waited at Eastgate.");

        final ContextPackage assembled = assemble(chunk, 1, SIX);

        assertThat(assembled.snapshot().glossary())
                .extracting(SnapshotTerm::term)
                .containsExactly("Harrow");
        assertThat(assembled.draftContext().glossaryLines()).containsExactly("Harrow → Герроу (place, unknown)");
    }

    @Test
    void batchOf_threeItems_carriesTheUnionOfTheirThreeSets() {
        final Chunk chunk = ContextFixtures.chunk("Corvin sat down.", "Fallow was far away.", "Harrow and Corvin met.");
        final List<DraftContext> perItem = List.of(
                assemble(chunk, 0, SIX).draftContext(),
                assemble(chunk, 1, SIX).draftContext(),
                assemble(chunk, 2, SIX).draftContext());

        final DraftContext joined = BatchContexts.of(List.of("1", "2", "3"), perItem, List.of(), null, List.of())
                .draft();

        assertThat(joined.glossaryLines())
                .containsExactlyInAnyOrder(
                        "Corvin → Корвін (place, unknown)",
                        "Fallow → Феллоу (place, unknown)",
                        "Harrow → Герроу (place, unknown)");
    }

    @ParameterizedTest(name = "[{index}] {0} in «{1}» -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Sendai|He flew to Ono-Sendai at dawn.|false",
                "Ono-Sendai|He flew to Ono-Sendai at dawn.|true",
                "Sendai|He flew to Sendai at dawn.|true",
                "Corvin|Corvin's hat blew off.|true",
                "Corvin|Both Corvins laughed.|true",
                "Corvin|Corvinian wine.|false",
                "Gentleman Loser|The Gentleman  Loser smiled.|true",
                "Case|He opened the case.|false",
                "Case|Case opened the door.|true",
                "Майлз|Майлза не було вдома.|true",
                "Майлз|Майлзові було байдуже.|false"
            })
    void occurrence_variousTexts_followsTheOneMatcher(final String term, final String text, final boolean expected) {
        final Chunk chunk = ContextFixtures.chunk(text);

        final ContextPackage assembled = assemble(chunk, 0, List.of(name(term, "x")));

        assertThat(assembled.snapshot().glossary().size()).isEqualTo(expected ? 1 : 0);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "like,подобатися,false",
        "just,просто,false",
        "program,програму,false",
        "program,програма,true",
        "console,консоль,true",
        "programs,програми,true"
    })
    void select_lexiconEntry_leavesOutFunctionWordsAndDeclinedForms(
            final String term, final String rendering, final boolean shown) {
        final Segment segment = ContextFixtures.segment(0, "The " + term + " was like that, just so.");

        final List<SnapshotRendering> selected = InjectedLexicon.select(
                List.of(segment),
                List.of(LexiconEntry.of("p1", term).seen(rendering)),
                List.of(),
                LexiconFilter.of("en", "uk"));

        assertThat(selected.size()).isEqualTo(shown ? 1 : 0);
    }

    @Test
    void select_lexiconTermHyphenJoinedToAnotherWord_isNotNamed() {
        final Segment segment = ContextFixtures.segment(0, "A jet-black coat.");

        final List<SnapshotRendering> selected = InjectedLexicon.select(
                List.of(segment), List.of(LexiconEntry.of("p1", "black").seen("чорний")), List.of());

        assertThat(selected).isEmpty();
    }

    @Test
    void replay_overWideSnapshot_shrinksToTheTextBeingTranslated() {
        final List<SnapshotTerm> wide = SIX.stream()
                .map(entry ->
                        new SnapshotTerm(entry.term(), entry.target(), entry.type(), entry.gender(), false, false))
                .toList();
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of("Раніше."),
                wide,
                List.of(),
                "A summary.",
                "Style.",
                List.of(new SnapshotRendering("master", "господар"), new SnapshotRendering("imp", "біс")),
                List.of("Lyra — female", "Hale — male"));
        final Segment segment = ContextFixtures.segment(0, "Harrow's master and Lyra left.");

        final DraftContext replayed =
                ContextPackageAssembler.replay(snapshot, ContextFixtures.mask(segment, SIX), segment);

        assertThat(replayed.glossaryLines()).containsExactly("Harrow → Герроу (place, unknown)");
        assertThat(replayed.lexiconLines()).containsExactly("master → господар");
        assertThat(replayed.characterLines()).containsExactly("Lyra — female");
        assertThat(replayed.precedingTargets()).containsExactly("Раніше.");
        assertThat(replayed.summary()).isEqualTo("A summary.");
    }

    @Test
    void assemble_tightAllowance_scopedGlossaryLeavesRoomForTheCharacterSheetAndTheSummary() {
        final GlossaryEntry lyra = ContextFixtures.entry("Lyra", "Ліра", TermType.CHARACTER, Gender.FEMALE, false);
        final List<GlossaryEntry> glossary = new ArrayList<>(SIX);
        glossary.add(lyra);
        final Chunk chunk =
                ContextFixtures.chunk("Corvin, Dunmere, Eastgate, Fallow, Gantry and Harrow.", "Lyra went to Harrow.");
        final Segment segment = chunk.segments().get(1);
        final ContextInputs inputs =
                new ContextInputs(ContextFixtures.STYLE, "Short.", 0, glossary, List.of(), ALLOWANCE);

        final ContextPackage assembled = ContextPackageAssembler.assemble(
                chunk, segment, ContextFixtures.mask(segment, glossary), ContextFixtures.NO_MEMORY, inputs);

        assertThat(assembled.snapshot().glossary())
                .extracting(SnapshotTerm::term)
                .containsExactlyInAnyOrder("Harrow", "Lyra");
        assertThat(assembled.snapshot().characters()).containsExactly("Lyra — female");
        assertThat(assembled.snapshot().summary()).isEqualTo("Short.");
    }
}
