package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.qa.CheckName;

/** Keeping an inline element that declares another language verbatim, behind one token, under Keep as-is. */
class ProtectedSpansKeptRunTest {

    private static final String FRENCH_MASKED = "She whispered ⟦g0⟧au revoir⟦g1⟧ and left.";
    private static final List<PlaceholderPair> FRENCH = List.of(ProtectedSpansFixtures.pair(0, 1, "fr"));

    private static ProtectedMask mask(
            final String masked,
            final List<PlaceholderPair> pairs,
            final String sourceLanguage,
            final ForeignPassagePolicy policy,
            final List<GlossaryEntry> glossary) {
        return ProtectedSpans.mask(ProtectedSpansFixtures.segment(masked, pairs), sourceLanguage, policy, glossary);
    }

    @Test
    void mask_foreignPairUnderKeep_collapsesItWithItsTextIntoOneTokenRestoredVerbatim() {
        final ProtectedMask mask = mask(FRENCH_MASKED, FRENCH, "en", ForeignPassagePolicy.KEEP, List.of());

        assertThat(mask.maskedText()).isEqualTo("She whispered ⟦g2⟧ and left.");
        assertThat(mask.spans()).containsExactly(new ProtectedSpan("⟦g2⟧", "⟦g0⟧au revoir⟦g1⟧", CheckName.KEPT_RUN));
        assertThat(mask.presentLocked()).isEmpty();
    }

    @Test
    void gate_replyKeepsTheTokenOnce_restoresTheRunForTheInnerGate() {
        final Segment segment = ProtectedSpansFixtures.segment(FRENCH_MASKED, FRENCH);
        final GateFunction gate = ProtectedSpans.gate(
                Map.of(segment.id(), mask(FRENCH_MASKED, FRENCH, "en", ForeignPassagePolicy.KEEP, List.of())),
                ProtectedSpansFixtures.PASSTHROUGH);

        final GateResult result = gate.restore(segment, "Вона прошепотіла ⟦g2⟧ і пішла.");

        assertThat(result)
                .isEqualTo(new GateResult.Restored(
                        "Вона прошепотіла ⟦g0⟧au revoir⟦g1⟧ і пішла.", "Вона прошепотіла ⟦g0⟧au revoir⟦g1⟧ і пішла."));
    }

    @Test
    void gate_replyRepeatsTheKeptRunsToken_failsAHighMarkupGate() {
        final Segment segment = ProtectedSpansFixtures.segment(FRENCH_MASKED, FRENCH);
        final GateFunction gate = ProtectedSpans.gate(
                Map.of(segment.id(), mask(FRENCH_MASKED, FRENCH, "en", ForeignPassagePolicy.KEEP, List.of())),
                ProtectedSpansFixtures.PASSTHROUGH);

        final GateResult result = gate.restore(segment, "Вона прошепотіла ⟦g2⟧ і пішла ⟦g2⟧.");

        assertThat(result).isInstanceOfSatisfying(GateResult.GateFailed.class, failed -> {
            assertThat(failed.finding().kind()).isEqualTo("markup");
            assertThat(failed.finding().severity()).isEqualTo(Severity.HIGH);
            assertThat(failed.finding().raisedBy()).isEqualTo("kept-run");
            assertThat(failed.finding().note())
                    .isEqualTo("The protected token ⟦g2⟧ came back 2 times instead of exactly once.");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairsThatAreNotKept")
    void mask_pairThatIsNotAForeignRunToKeep_leavesTheDocumentTokens(
            final String name, final String language, final String source, final ForeignPassagePolicy policy) {
        final List<PlaceholderPair> pairs = List.of(ProtectedSpansFixtures.pair(0, 1, language));

        final ProtectedMask mask = mask("⟦g0⟧carpe diem⟦g1⟧", pairs, source, policy, List.of());

        assertThat(mask.maskedText()).isEqualTo("⟦g0⟧carpe diem⟦g1⟧");
        assertThat(mask.spans()).isEmpty();
    }

    private static Stream<Arguments> pairsThatAreNotKept() {
        return Stream.of(
                Arguments.of("the same language with a region", "en-GB", "en", ForeignPassagePolicy.KEEP),
                Arguments.of("latin under a latin source", "la", "la", ForeignPassagePolicy.KEEP),
                Arguments.of("the Translate policy", "fr", "en", ForeignPassagePolicy.TRANSLATE),
                Arguments.of("the Translate-with-note policy", "fr", "en", ForeignPassagePolicy.TRANSLATE_WITH_NOTE),
                Arguments.of("a language that names nothing", "xx", "en", ForeignPassagePolicy.KEEP),
                Arguments.of("an unknown source language", "fr", null, ForeignPassagePolicy.KEEP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recognizedForeignLanguages")
    void mask_recognizedOtherLanguage_isKeptWhateverItsSpelling(final String language) {
        final ProtectedMask mask = mask(
                "⟦g0⟧carpe diem⟦g1⟧",
                List.of(ProtectedSpansFixtures.pair(0, 1, language)),
                "en",
                ForeignPassagePolicy.KEEP,
                List.of());

        assertThat(mask.maskedText()).isEqualTo("⟦g2⟧");
    }

    private static Stream<String> recognizedForeignLanguages() {
        return Stream.of("la", "fr", "FR_ca", "de-AT");
    }

    @Test
    void mask_pairWithNoDeclaredLanguage_isNotAForeignRun() {
        final ProtectedMask mask = mask(
                "⟦g0⟧old⟦g1⟧ door",
                List.of(ProtectedSpansFixtures.pair(0, 1, null)),
                "en",
                ForeignPassagePolicy.KEEP,
                List.of());

        assertThat(mask.maskedText()).isEqualTo("⟦g0⟧old⟦g1⟧ door");
    }

    @Test
    void mask_termAndForeignRun_numbersTheirTokensInDocumentOrder() {
        final ProtectedMask mask = mask(
                "Hale said ⟦g0⟧au revoir⟦g1⟧.",
                FRENCH,
                "en",
                ForeignPassagePolicy.KEEP,
                List.of(ProtectedSpansFixtures.locked("Hale", "Гейл")));

        assertThat(mask.maskedText()).isEqualTo("⟦g2⟧ said ⟦g3⟧.");
        assertThat(mask.spans())
                .extracting(ProtectedSpan::check)
                .containsExactly(CheckName.LOCKED_TERM, CheckName.KEPT_RUN);
    }

    @Test
    void mask_lockedTermInsideAForeignRun_staysInsideTheRun() {
        final ProtectedMask mask = mask(
                "⟦g0⟧Hale a dit⟦g1⟧ hello.",
                FRENCH,
                "en",
                ForeignPassagePolicy.KEEP,
                List.of(ProtectedSpansFixtures.locked("Hale", "Гейл")));

        assertThat(mask.maskedText()).isEqualTo("⟦g2⟧ hello.");
        assertThat(mask.presentLocked()).isEmpty();
    }

    @Test
    void mask_foreignRunEnclosingAnotherPair_keepsTheWholeRunAsOneToken() {
        final List<PlaceholderPair> pairs =
                List.of(ProtectedSpansFixtures.pair(0, 3, "fr"), ProtectedSpansFixtures.pair(1, 2, "fr"));

        final ProtectedMask mask =
                mask("⟦g0⟧un ⟦g1⟧petit⟦g2⟧ chat⟦g3⟧.", pairs, "en", ForeignPassagePolicy.KEEP, List.of());

        assertThat(mask.maskedText()).isEqualTo("⟦g4⟧.");
        assertThat(mask.spans())
                .containsExactly(new ProtectedSpan("⟦g4⟧", "⟦g0⟧un ⟦g1⟧petit⟦g2⟧ chat⟦g3⟧", CheckName.KEPT_RUN));
    }

    @Test
    void checkRestored_storedTargetHoldingTheRunVerbatim_answersItReMasked() {
        final ProtectedMask mask = mask(FRENCH_MASKED, FRENCH, "en", ForeignPassagePolicy.KEEP, List.of());

        assertThat(ProtectedSpans.checkRestored("Вона прошепотіла ⟦g0⟧au revoir⟦g1⟧ і пішла.", mask)
                        .data())
                .isEqualTo("Вона прошепотіла ⟦g2⟧ і пішла.");
    }

    @Test
    void checkRestored_storedTargetWithTheRunTranslated_fails() {
        final ProtectedMask mask = mask(FRENCH_MASKED, FRENCH, "en", ForeignPassagePolicy.KEEP, List.of());

        assertThat(ProtectedSpans.checkRestored("Вона прошепотіла ⟦g0⟧до побачення⟦g1⟧ і пішла.", mask)
                        .isErr())
                .isTrue();
    }
}
