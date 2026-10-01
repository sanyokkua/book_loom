package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.qa.CheckName;

/**
 * Keeping a whole block that declares another language verbatim under Keep as-is: the fixture book's Latin paragraph
 * {@code <p xml:lang="la">} was translated and judged 0.95 while its policy said keep.
 */
class ProtectedSpansKeptBlockTest {

    private static final String LATIN = "Gravitas omnia trahit, sed nemo videt.";
    private static final String FRENCH_INSIDE = "Gravitas ⟦g0⟧omnia⟦g1⟧ trahit.";

    private static CallFrame frame(
            final String source, final @Nullable String book, final ForeignPassagePolicy policy) {
        return new CallFrame(source, "uk", StyleSheet.from(BookBrief.defaults(source)), policy, book);
    }

    @Test
    void mask_latinBlockUnderKeep_isOneTokenRestoredVerbatim() {
        final ProtectedMask mask = ProtectedSpans.mask(
                ProtectedSpansFixtures.declaring(LATIN, List.of(), "la"),
                frame("en", "en", ForeignPassagePolicy.KEEP),
                List.of());

        assertThat(mask.maskedText()).isEqualTo("⟦g0⟧");
        assertThat(mask.spans()).containsExactly(new ProtectedSpan("⟦g0⟧", LATIN, CheckName.KEPT_RUN));
    }

    @Test
    void mask_foreignBlockWithItsOwnMarkup_keepsTheMarkupInsideTheOneToken() {
        final ProtectedMask mask = ProtectedSpans.mask(
                ProtectedSpansFixtures.declaring(FRENCH_INSIDE, List.of(ProtectedSpansFixtures.pair(0, 1, null)), "la"),
                frame("en", "en", ForeignPassagePolicy.KEEP),
                List.of());

        assertThat(mask.maskedText()).isEqualTo("⟦g2⟧");
        assertThat(mask.spans()).containsExactly(new ProtectedSpan("⟦g2⟧", FRENCH_INSIDE, CheckName.KEPT_RUN));
    }

    // A block declaring the book's own language is the book's text, even when the brief names another source; a
    // region variant of the source is not foreign; the other two policies translate the block.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            the book's own language under a brief naming another | en | de | en | KEEP
            a region variant of the source                       | en-GB | en | en | KEEP
            Translate                                            | la | en | en | TRANSLATE
            Translate with a note                                | la | en | en | TRANSLATE_WITH_NOTE
            no declaration on the block                          |    | en | en | KEEP
            """)
    void mask_blockThatIsNotAForeignPassageToKeep_isShownAsItIs(
            final String name,
            final @Nullable String declared,
            final String source,
            final String book,
            final ForeignPassagePolicy policy) {
        final ProtectedMask mask = ProtectedSpans.mask(
                ProtectedSpansFixtures.declaring(LATIN, List.of(), declared), frame(source, book, policy), List.of());

        assertThat(mask.maskedText()).isEqualTo(LATIN);
        assertThat(mask.spans()).isEmpty();
    }
}
