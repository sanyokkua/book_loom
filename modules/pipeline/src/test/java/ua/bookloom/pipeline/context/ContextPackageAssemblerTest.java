package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.memory.TmLookup;

/** Which glossary terms and earlier targets a draft is shown, and what the snapshot records of them. */
class ContextPackageAssemblerTest {

    private static final GlossaryEntry HALE =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry BAKER_STREET =
            ContextFixtures.entry("Baker Street", "Бейкер-стріт", TermType.PLACE, Gender.UNKNOWN, false);
    private static final GlossaryEntry MILTON =
            ContextFixtures.entry("Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false);
    private static final GlossaryEntry MOREAU =
            ContextFixtures.entry("Moreau", null, TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry HALE_LOCKED =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);

    private static ContextPackage assemble(
            final Chunk chunk, final int index, final ContextInputs inputs, final TmLookup memory) {
        final Segment segment = chunk.segments().get(index);
        return ContextPackageAssembler.assemble(
                chunk, segment, ContextFixtures.mask(segment, inputs.glossary()), memory, inputs);
    }

    private static List<String> glossaryLines(final Chunk chunk, final int index, final List<GlossaryEntry> glossary) {
        return assemble(chunk, index, ContextFixtures.inputs(null, 0, glossary, List.of()), ContextFixtures.NO_MEMORY)
                .draftContext()
                .glossaryLines();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("termCases")
    void assemble_glossaryAndChunk_listsOnlyTheTermsThatOccurWholeWord(
            final String name, final List<GlossaryEntry> glossary, final String text, final List<String> expected) {
        assertThat(glossaryLines(ContextFixtures.chunk(text), 0, glossary)).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> termCases() {
        return Stream.concat(occurrenceCases(), genderLineCases());
    }

    private static Stream<Arguments> occurrenceCases() {
        return Stream.of(
                Arguments.of(
                        "only the term in the chunk",
                        List.of(HALE, BAKER_STREET),
                        "Hale opened the door.",
                        List.of("Hale → Гейл (character, male)")),
                Arguments.of(
                        "an unlocked place with a target",
                        List.of(HALE, MILTON),
                        "Milton was quiet.",
                        List.of("Milton → Мілтон (place)")),
                Arguments.of(
                        "an entry with no target",
                        List.of(MOREAU),
                        "Moreau laughed.",
                        List.of("Moreau (character, male)")),
                Arguments.of("an empty glossary", List.of(), "Hale opened the door.", List.of()),
                Arguments.of("a term inside a longer word", List.of(HALE), "The whale sang.", List.of()),
                Arguments.of(
                        "a locked entry hidden behind a token",
                        List.of(HALE_LOCKED),
                        "Hale opened the door.",
                        List.of("⟦g0⟧ → Гейл, character, male")),
                Arguments.of(
                        "a locked entry with no target",
                        List.of(ContextFixtures.entry("Moreau", null, TermType.CHARACTER, Gender.MALE, true)),
                        "Moreau laughed.",
                        List.of("Moreau (character, male)")));
    }

    // Only a character has a gender line, and a guessed gender is worded as likely (15i.T1).
    private static Stream<Arguments> genderLineCases() {
        return Stream.of(
                Arguments.of(
                        "a place with a gender has no gender line",
                        List.of(ContextFixtures.entry("Milton", "Мілтон", TermType.PLACE, Gender.NEUTER, false)),
                        "Milton was quiet.",
                        List.of("Milton → Мілтон (place)")),
                Arguments.of(
                        "a term with a gender has no gender line",
                        List.of(ContextFixtures.entry("Master", "Майстер", TermType.TERM, Gender.MALE, false)),
                        "The Master left.",
                        List.of("Master → Майстер (term)")),
                Arguments.of(
                        "a locked place with a gender has no gender line",
                        List.of(ContextFixtures.entry("Milton", "Мілтон", TermType.PLACE, Gender.NEUTER, true)),
                        "Milton was quiet.",
                        List.of("⟦g0⟧ → Мілтон, place")),
                Arguments.of(
                        "a character whose gender is only suggested is a likely one",
                        List.of(ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.UNKNOWN, false)
                                .withSuggestedGender(Gender.MALE)),
                        "Hale opened the door.",
                        List.of("Hale → Гейл (character, likely male)")));
    }

    @Test
    void assemble_suggestedTarget_isListedApartFromThePersonsRenderings() {
        final GlossaryEntry suggested = HALE.withSuggestedTarget("Гейл");
        final Chunk chunk = ContextFixtures.chunk("Hale met Milton.");
        final List<GlossaryEntry> glossary = List.of(suggested, MILTON);

        final ContextPackage context =
                assemble(chunk, 0, ContextFixtures.inputs(null, 0, glossary, List.of()), ContextFixtures.NO_MEMORY);

        assertThat(context.draftContext().glossaryLines()).containsExactly("Milton → Мілтон (place)");
        assertThat(context.draftContext().suggestedLines()).containsExactly("Hale → Гейл (character, male)");
        assertThat(context.snapshot().glossary())
                .extracting(SnapshotTerm::suggested)
                .containsExactly(true, false);
    }

    @Test
    void replay_snapshotWithASuggestedTerm_listsItApartAgain() {
        final Chunk chunk = ContextFixtures.chunk("Hale opened the door.");
        final List<GlossaryEntry> glossary = List.of(HALE.withSuggestedTarget("Гейл"));
        final ContextPackage first =
                assemble(chunk, 0, ContextFixtures.inputs(null, 0, glossary, List.of()), ContextFixtures.NO_MEMORY);

        final var replayed = ContextPackageAssembler.replay(
                first.snapshot(),
                ContextFixtures.mask(chunk.segments().getFirst(), glossary),
                chunk.segments().getFirst());

        assertThat(replayed.glossaryLines()).isEmpty();
        assertThat(replayed.suggestedLines()).containsExactly("Hale → Гейл (character, male)");
    }

    @Test
    void assemble_termOnlyInAnotherSegmentOfTheChunk_isNotListed() {
        final Chunk chunk = ContextFixtures.chunk("It was late.", "Milton was quiet.");

        assertThat(glossaryLines(chunk, 0, List.of(MILTON))).isEmpty();
        assertThat(glossaryLines(chunk, 1, List.of(MILTON))).containsExactly("Milton → Мілтон (place)");
    }

    @Test
    void assemble_termNextToAFootnoteMarker_isStillFoundAndLockedOneStaysHidden() {
        final Chunk chunk = ContextFixtures.chunk("Hale⟦g0⟧1⟦g1⟧ opened the door.");

        assertThat(glossaryLines(chunk, 0, List.of(HALE_LOCKED))).containsExactly("⟦g2⟧ → Гейл, character, male");
        assertThat(glossaryLines(chunk, 0, List.of(HALE))).containsExactly("Hale → Гейл (character, male)");
    }

    @Test
    void assemble_lockedEntryHiddenOnlyInAnotherSegment_isLeftOut() {
        final Chunk chunk = ContextFixtures.chunk("It was late.", "Hale opened the door.");

        assertThat(glossaryLines(chunk, 0, List.of(HALE_LOCKED))).isEmpty();
    }

    @Test
    void assemble_lockedEntryHidden_doesNotNameTheTermItself() {
        final Chunk chunk = ContextFixtures.chunk("Hale opened the door.");

        assertThat(String.join("\n", glossaryLines(chunk, 0, List.of(HALE_LOCKED))))
                .doesNotContain("Hale");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("precedingCases")
    void assemble_earlierTargets_carriesTheLastNAsPlainText(
            final String name, final int count, final List<String> earlier, final List<String> expected) {
        final ContextInputs inputs = ContextFixtures.inputs(null, count, List.of(), earlier);

        final ContextPackage pack = assemble(ContextFixtures.chunk("He left."), 0, inputs, ContextFixtures.NO_MEMORY);

        assertThat(pack.draftContext().precedingTargets()).containsExactlyElementsOf(expected);
        assertThat(pack.snapshot().precedingTargets()).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> precedingCases() {
        return Stream.of(
                Arguments.of(
                        "Balanced keeps the last two", 2, List.of("Один.", "Два.", "Три."), List.of("Два.", "Три.")),
                Arguments.of("Fast keeps the last one", 1, List.of("Один.", "Два.", "Три."), List.of("Три.")),
                Arguments.of(
                        "Max keeps three",
                        3,
                        List.of("Один.", "Два.", "Три.", "Чотири."),
                        List.of("Два.", "Три.", "Чотири.")),
                Arguments.of("a unit's start has none", 2, List.of(), List.of()),
                Arguments.of("fewer than N earlier", 3, List.of("Один."), List.of("Один.")),
                Arguments.of(
                        "markup is shown as plain text",
                        2,
                        List.of("Він відчинив ⟦g0⟧старі⟦g1⟧ двері."),
                        List.of("Він відчинив старі двері.")),
                Arguments.of("a blank one is dropped", 2, List.of("Один.", "⟦g0⟧⟦g1⟧"), List.of("Один.")));
    }

    @Test
    void assemble_snapshot_holdsTextsSummaryAndStyleSheet() {
        final ContextInputs inputs = ContextFixtures.inputs("Hale is a detective.", 2, List.of(HALE), List.of());

        final ContextPackage pack =
                assemble(ContextFixtures.chunk("Hale opened it."), 0, inputs, ContextFixtures.NO_MEMORY);

        assertThat(pack.snapshot().glossary())
                .containsExactly(new SnapshotTerm("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false, false, null));
        assertThat(pack.snapshot().summary()).isEqualTo("Hale is a detective.");
        assertThat(pack.snapshot().styleSheet()).isEqualTo("Neutral register.");
        assertThat(pack.draftContext().summary()).isEqualTo("Hale is a detective.");
    }

    // IF the snapshot dropped who decided a gender, THEN a retry with the first draft's context would show a guess as
    // the person's word (15i.T1).
    @Test
    void assemble_snapshot_keepsWhoDecidedEachGender() {
        final GlossaryEntry suggested = ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE);
        final GlossaryEntry verified = ContextFixtures.entry("Lyra", "Ліра", TermType.CHARACTER, Gender.UNKNOWN, false)
                .withInferredGender(Gender.FEMALE);
        final GlossaryEntry person = ContextFixtures.entry("Sam", "Сем", TermType.CHARACTER, Gender.MALE, false);

        final ContextPackage pack = assemble(
                ContextFixtures.chunk("Hale, Lyra and Sam met."),
                0,
                ContextFixtures.inputs(null, 0, List.of(suggested, verified, person), List.of()),
                ContextFixtures.NO_MEMORY);

        assertThat(pack.snapshot().glossary())
                .extracting(SnapshotTerm::term, SnapshotTerm::genderOrigin)
                .containsExactly(
                        tuple("Hale", GenderOrigin.SUGGESTED),
                        tuple("Lyra", GenderOrigin.VERIFIED),
                        tuple("Sam", GenderOrigin.PERSON));
    }

    @Test
    void assemble_glossaryChangedAfterwards_snapshotKeepsTheRenderingItSaw() {
        final List<GlossaryEntry> glossary = new ArrayList<>(List.of(HALE));
        final ContextPackage pack = assemble(
                ContextFixtures.chunk("Hale opened it."),
                0,
                ContextFixtures.inputs(null, 0, glossary, List.of()),
                ContextFixtures.NO_MEMORY);

        glossary.set(0, ContextFixtures.entry("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, false));

        assertThat(pack.snapshot().glossary()).extracting(SnapshotTerm::target).containsExactly("Гейл");
    }

    @Test
    void assemble_noSummary_leavesItNullInContextAndSnapshot() {
        final ContextPackage pack = assemble(
                ContextFixtures.chunk("Hello."),
                0,
                ContextFixtures.inputs(null, 0, List.of(), List.of()),
                ContextFixtures.NO_MEMORY);

        assertThat(pack.draftContext().summary()).isNull();
        assertThat(pack.snapshot().summary()).isNull();
    }
}
