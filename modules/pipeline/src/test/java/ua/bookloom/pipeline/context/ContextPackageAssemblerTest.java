package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
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
                        List.of("Milton → Мілтон (place, unknown)")),
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

    @Test
    void assemble_termOnlyInAnotherSegmentOfTheChunk_isStillListed() {
        final Chunk chunk = ContextFixtures.chunk("It was late.", "Milton was quiet.");

        assertThat(glossaryLines(chunk, 0, List.of(MILTON))).containsExactly("Milton → Мілтон (place, unknown)");
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
                .containsExactly(new SnapshotTerm("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false));
        assertThat(pack.snapshot().summary()).isEqualTo("Hale is a detective.");
        assertThat(pack.snapshot().styleSheet()).isEqualTo("Neutral register.");
        assertThat(pack.draftContext().summary()).isEqualTo("Hale is a detective.");
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
