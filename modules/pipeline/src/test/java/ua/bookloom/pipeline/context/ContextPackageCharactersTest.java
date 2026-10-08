package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.prompt.DraftContext;

/** The character gender sheet: only the characters a segment names, only those whose gender is known, within a share. */
class ContextPackageCharactersTest {

    private static final GlossaryEntry LYRA =
            ContextFixtures.entry("Lyra", "Ліра", TermType.CHARACTER, Gender.FEMALE, false);
    private static final GlossaryEntry HALE =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry NOBODY =
            ContextFixtures.entry("Quill", "Квіл", TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final GlossaryEntry PLACE =
            ContextFixtures.entry("Oxford", "Оксфорд", TermType.PLACE, Gender.NEUTER, false);

    private static ContextPackage assemble(final String text, final int allowance) {
        final Chunk chunk = ContextFixtures.chunk(text);
        final Segment segment = chunk.segments().getFirst();
        final List<GlossaryEntry> glossary = List.of(LYRA, HALE, NOBODY, PLACE);
        final ContextInputs inputs = new ContextInputs(
                ContextFixtures.STYLE, null, 0, glossary, List.of(), allowance, List.of(), LexiconFilter.NONE, "en");
        return ContextPackageAssembler.assemble(
                chunk, segment, ContextFixtures.mask(segment, glossary), ContextFixtures.NO_MEMORY, inputs);
    }

    @Test
    void assemble_segmentNamesOneCharacter_listsOnlyThatCharactersGender() {
        final ContextPackage assembled = assemble("Lyra walked to Oxford with Quill.", 1000);

        assertThat(assembled.draftContext().characterLines()).containsExactly("Lyra — female");
        assertThat(assembled.snapshot().characters()).containsExactly("Lyra — female");
    }

    @Test
    void assemble_pronounsFollowTheName_showTheEvidenceCompactly() {
        final ContextPackage assembled = assemble("Lyra walked home. She sat down, and Hale watched her.", 1000);

        assertThat(assembled.draftContext().characterLines()).containsExactly("Lyra — female (she ×1)", "Hale — male");
    }

    @Test
    void assemble_segmentNamesNoCharacter_hasNoSheet() {
        assertThat(assemble("The rain fell on Oxford.", 1000).draftContext().characterLines())
                .isEmpty();
    }

    @Test
    void assemble_twoCharacters_listsBothInGlossaryOrder() {
        assertThat(assemble("Hale met Lyra.", 1000).draftContext().characterLines())
                .containsExactly("Lyra — female", "Hale — male");
    }

    @Test
    void assemble_tinyAllowance_cutsTheSheetToItsShareInsteadOfTakingTheGlossaryRoom() {
        final DraftContext context = assemble("Hale met Lyra.", 12).draftContext();

        assertThat(context.glossaryLines()).hasSize(1);
        assertThat(context.characterLines()).isEmpty();
    }

    @Test
    void replay_snapshotWithASheet_rebuildsTheSameLines() {
        final ContextPackage assembled = assemble("Hale met Lyra.", 1000);
        final Segment segment = ContextFixtures.segment(0, "Hale met Lyra.");

        final DraftContext replayed =
                ContextPackageAssembler.replay(assembled.snapshot(), ContextFixtures.mask(segment, List.of()), segment);

        assertThat(replayed.characterLines()).containsExactly("Lyra — female", "Hale — male");
    }
}
