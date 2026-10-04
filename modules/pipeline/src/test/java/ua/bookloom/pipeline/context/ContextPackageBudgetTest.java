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

/** The dynamic context is cut by priority when its allowance is short, and the snapshot records what was shown. */
class ContextPackageBudgetTest {

    private static final GlossaryEntry HALE =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
    private static final String SUMMARY = "Hale searches for his brother.";

    private static ContextPackage assemble(final int allowance) {
        final Chunk chunk = ContextFixtures.chunk("Hale opened the door.");
        final Segment segment = chunk.segments().getFirst();
        final List<GlossaryEntry> glossary = List.of(HALE);
        final ContextInputs inputs =
                new ContextInputs(ContextFixtures.STYLE, SUMMARY, 2, glossary, List.of("Один.", "Два."), allowance);
        return ContextPackageAssembler.assemble(
                chunk, segment, ContextFixtures.mask(segment, glossary), ContextFixtures.NO_MEMORY, inputs);
    }

    // "Hale → Гейл (character, male)" costs 12 tokens, each preceding text 2, the summary 12.
    @Test
    void assemble_allowanceHoldsEverything_showsAllSections() {
        final DraftContext context = assemble(1000).draftContext();

        assertThat(context.glossaryLines()).hasSize(1);
        assertThat(context.precedingTargets()).containsExactly("Один.", "Два.");
        assertThat(context.summary()).isEqualTo(SUMMARY);
    }

    @Test
    void assemble_allowanceShortOfTheSummary_cutsTheSummaryFirst() {
        final DraftContext context = assemble(20).draftContext();

        assertThat(context.summary()).isNull();
        assertThat(context.glossaryLines()).hasSize(1);
        assertThat(context.precedingTargets()).containsExactly("Один.", "Два.");
    }

    @Test
    void assemble_allowanceHoldsOnePrecedingText_keepsTheNewestOne() {
        final DraftContext context = assemble(14).draftContext();

        assertThat(context.precedingTargets()).containsExactly("Два.");
        assertThat(context.glossaryLines()).hasSize(1);
    }

    @Test
    void assemble_noAllowance_leavesEverythingOut() {
        final ContextPackage assembled = assemble(0);

        assertThat(assembled.draftContext().glossaryLines()).isEmpty();
        assertThat(assembled.draftContext().precedingTargets()).isEmpty();
        assertThat(assembled.draftContext().summary()).isNull();
        assertThat(assembled.snapshot().glossary()).isEmpty();
    }

    @Test
    void assemble_allowanceShortOfTheGlossaryLine_stillSpendsWhatIsLeftOnLowerSections() {
        final DraftContext context = assemble(11).draftContext();

        assertThat(context.glossaryLines()).isEmpty();
        assertThat(context.precedingTargets()).containsExactly("Один.", "Два.");
        assertThat(context.summary()).isNull();
    }

    @Test
    void assemble_cutContext_snapshotRecordsExactlyWhatWasShown() {
        final ContextPackage assembled = assemble(14);

        assertThat(assembled.snapshot().precedingTargets()).containsExactly("Два.");
        assertThat(assembled.snapshot().summary()).isNull();
    }
}
