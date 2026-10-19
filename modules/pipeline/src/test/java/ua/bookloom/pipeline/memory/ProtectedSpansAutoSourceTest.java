package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.qa.CheckName;

/**
 * Under Auto the run has no source language to compare a marked run with, so a run is kept when it is written in a
 * script other than the one the text is mostly written in; a passage in the same script is left to the prompt's
 * foreign-passage rule.
 */
class ProtectedSpansAutoSourceTest {

    private static ProtectedMask autoMask(
            final String masked, final @Nullable String pairLanguage, final ForeignPassagePolicy policy) {
        final List<PlaceholderPair> pairs = List.of(ProtectedSpansFixtures.pair(0, 1, pairLanguage));
        return ProtectedSpans.mask(ProtectedSpansFixtures.segment(masked, pairs), null, policy, List.of());
    }

    // IF a Cyrillic run in a Latin book reached the model, THEN it would be translated although the book marks it.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            value = {
                "a Cyrillic run in a Latin text, no declared language|He whispered ⟦g0⟧Здравствуйте⟦g1⟧ and left.|NULL",
                "a Cyrillic run in a Latin text, declared ru|He whispered ⟦g0⟧Здравствуйте⟦g1⟧ and left.|ru",
                "a Latin run in a Cyrillic text, no declared language|Вона прошепотіла ⟦g0⟧au revoir⟦g1⟧ і пішла.|NULL",
                "a Greek run in a Latin text|He wrote ⟦g0⟧λόγος⟦g1⟧ twice.|NULL"
            },
            delimiter = '|',
            nullValues = "NULL")
    void mask_runInAScriptOtherThanTheTextsMajority_isKeptBehindOneToken(
            final String name, final String masked, final String pairLanguage) {
        final ProtectedMask mask = autoMask(masked, pairLanguage, ForeignPassagePolicy.KEEP);

        assertThat(mask.spans()).hasSize(1);
        assertThat(mask.spans().getFirst().check()).isEqualTo(CheckName.KEPT_RUN);
        assertThat(mask.maskedText()).contains("⟦g2⟧").doesNotContain("⟦g0⟧", "⟦g1⟧");
    }

    // IF a French phrase in an English book were kept, THEN Auto would freeze every borrowed word in the book.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            value = {
                "a French phrase declared fr|She whispered ⟦g0⟧au revoir⟦g1⟧ and left.|fr",
                "a French phrase with no declaration|She whispered ⟦g0⟧au revoir⟦g1⟧ and left.|NULL",
                "an English phrase in an English text|She whispered ⟦g0⟧goodbye⟦g1⟧ and left.|en",
                "a Cyrillic run in a Cyrillic text|Вона прошепотіла ⟦g0⟧привіт⟦g1⟧ і пішла.|uk",
                "a run of digits only|In ⟦g0⟧1914⟦g1⟧ the war began.|NULL"
            },
            delimiter = '|',
            nullValues = "NULL")
    void mask_runInTheTextsOwnScript_isLeftToThePromptRule(
            final String name, final String masked, final String pairLanguage) {
        final ProtectedMask mask = autoMask(masked, pairLanguage, ForeignPassagePolicy.KEEP);

        assertThat(mask.maskedText()).isEqualTo(masked);
        assertThat(mask.spans()).isEmpty();
    }

    // IF Translate also kept runs, THEN the policy the person chose would be ignored under Auto.
    @Test
    void mask_cyrillicRunInALatinTextUnderTranslate_isNotKept() {
        final String masked = "He whispered ⟦g0⟧Здравствуйте⟦g1⟧ and left.";

        final ProtectedMask mask = autoMask(masked, null, ForeignPassagePolicy.TRANSLATE);

        assertThat(mask.maskedText()).isEqualTo(masked);
        assertThat(mask.spans()).isEmpty();
    }

    // Naming the source language restores the declared-language rule: a Cyrillic pair with no declaration is not
    // foreign.
    @Test
    void mask_cyrillicRunWithNoDeclarationAndANamedSource_isNotKept() {
        final String masked = "He whispered ⟦g0⟧Здравствуйте⟦g1⟧ and left.";
        final List<PlaceholderPair> pairs = List.of(ProtectedSpansFixtures.pair(0, 1, null));

        final ProtectedMask mask = ProtectedSpans.mask(
                ProtectedSpansFixtures.segment(masked, pairs), "en", ForeignPassagePolicy.KEEP, List.of());

        assertThat(mask.maskedText()).isEqualTo(masked);
    }

    // IF the segment's own text decided, THEN a segment that is only a Cyrillic run would be "mostly Cyrillic" and its
    // run would reach the model; the book's majority script, counted once over the whole story, decides instead.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            value = {
                "a Cyrillic-only segment in a Latin book|⟦g0⟧Здравствуйте⟦g1⟧|LATIN|true",
                "a Cyrillic-only segment in a Cyrillic book|⟦g0⟧Здравствуйте⟦g1⟧|CYRILLIC|false"
            },
            delimiter = '|')
    void mask_withTheBooksMajorityScript_decidesByTheBookNotTheSegment(
            final String name, final String masked, final Character.UnicodeScript bookScript, final boolean isKept) {
        final List<PlaceholderPair> pairs = List.of(ProtectedSpansFixtures.pair(0, 1, null));

        final ProtectedMask mask = ProtectedSpans.mask(
                ProtectedSpansFixtures.segment(masked, pairs), null, bookScript, ForeignPassagePolicy.KEEP, List.of());

        assertThat(mask.spans().isEmpty()).isEqualTo(!isKept);
    }
}
