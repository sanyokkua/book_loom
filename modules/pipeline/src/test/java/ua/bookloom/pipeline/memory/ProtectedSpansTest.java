package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.LockedRendering;

/** Hiding a segment's locked glossary terms behind tokens and restoring them through the typed gate. */
class ProtectedSpansTest {

    private static final GlossaryEntry HALE = ProtectedSpansFixtures.locked("Hale", "Гейл");

    private static ProtectedMask mask(final String masked, final GlossaryEntry... glossary) {
        return ProtectedSpans.mask(
                ProtectedSpansFixtures.segment(masked), "en", ForeignPassagePolicy.KEEP, List.of(glossary));
    }

    @Test
    void mask_lockedTermWithTarget_replacesItWithATokenAndListsItAsPresent() {
        final ProtectedMask mask = mask("Hale opened the door.", HALE);

        assertThat(mask.maskedText()).isEqualTo("⟦g0⟧ opened the door.");
        assertThat(mask.spans()).containsExactly(new ProtectedSpan("⟦g0⟧", "Гейл", CheckName.LOCKED_TERM));
        assertThat(mask.presentLocked()).containsExactly(new LockedRendering("Hale", "Гейл"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textsThatStayAsWritten")
    void mask_textWithNoLockedWholeWordMatch_staysAsWritten(
            final String name, final String masked, final List<GlossaryEntry> glossary) {
        final ProtectedMask mask =
                ProtectedSpans.mask(ProtectedSpansFixtures.segment(masked), "en", ForeignPassagePolicy.KEEP, glossary);

        assertThat(mask.maskedText()).isEqualTo(masked);
        assertThat(mask.spans()).isEmpty();
        assertThat(mask.presentLocked()).isEmpty();
    }

    private static Stream<Arguments> textsThatStayAsWritten() {
        return Stream.of(
                Arguments.of("inside a longer word and with another case", "A whale and a Whale-boat.", List.of(HALE)),
                Arguments.of("lower-case where the term is capitalised", "hale opened it.", List.of(HALE)),
                Arguments.of(
                        "an unlocked entry",
                        "Milton opened it.",
                        List.of(ProtectedSpansFixtures.unlocked("Milton", "Мілтон"))),
                Arguments.of(
                        "a locked entry with no target",
                        "Milton opened it.",
                        List.of(ProtectedSpansFixtures.locked("Milton", null))),
                Arguments.of(
                        "a locked entry with a blank target",
                        "Milton opened it.",
                        List.of(ProtectedSpansFixtures.locked("Milton", "  "))),
                Arguments.of("no entry at all", "Hale opened it.", List.<GlossaryEntry>of()));
    }

    @Test
    void mask_baseAndLongerLockedTerm_longestMatchesFirstAndTokensFollowDocumentOrder() {
        final ProtectedMask mask = mask(
                "Baker Street was quiet; Baker left.",
                ProtectedSpansFixtures.locked("Baker", "Бейкер"),
                ProtectedSpansFixtures.locked("Baker Street", "Бейкер-стрит"));

        assertThat(mask.maskedText()).isEqualTo("⟦g0⟧ was quiet; ⟦g1⟧ left.");
        assertThat(mask.spans())
                .containsExactly(
                        new ProtectedSpan("⟦g0⟧", "Бейкер-стрит", CheckName.LOCKED_TERM),
                        new ProtectedSpan("⟦g1⟧", "Бейкер", CheckName.LOCKED_TERM));
    }

    @Test
    void mask_termBetweenExistingTokens_numbersAboveTheHighestTokenInDocumentOrder() {
        final ProtectedMask mask = mask("He met ⟦g0⟧old Hale⟦g1⟧ today.", HALE);

        assertThat(mask.maskedText()).isEqualTo("He met ⟦g0⟧old ⟦g2⟧⟦g1⟧ today.");
        assertThat(DraftPromptBuilder.expectedTokenSequence(mask.maskedText())).isEqualTo("⟦g0⟧ ⟦g2⟧ ⟦g1⟧");
    }

    @Test
    void mask_termThatSpellsATokenKey_isMaskedAsAWordButNeverInsideAnExistingToken() {
        final ProtectedMask mask = mask("He typed g0 in ⟦g0⟧old⟦g1⟧.", ProtectedSpansFixtures.locked("g0", "джі-нуль"));

        assertThat(mask.maskedText()).isEqualTo("He typed ⟦g2⟧ in ⟦g0⟧old⟦g1⟧.");
        assertThat(mask.spans()).containsExactly(new ProtectedSpan("⟦g2⟧", "джі-нуль", CheckName.LOCKED_TERM));
    }

    @Test
    void mask_sameTermTwice_listsItOnceAsPresentAndMasksBothOccurrences() {
        final ProtectedMask mask = mask("Hale saw Hale.", HALE);

        assertThat(mask.maskedText()).isEqualTo("⟦g0⟧ saw ⟦g1⟧.");
        assertThat(mask.presentLocked()).containsExactly(new LockedRendering("Hale", "Гейл"));
    }

    @Test
    void gate_replyReturnsTheTokenOnce_restoresTheNameAndHandsTheTextToTheInnerGate() {
        final Segment segment = ProtectedSpansFixtures.segment("Hale opened the door.");
        final List<String> seenByInner = new ArrayList<>();
        final GateFunction inner = (given, reply) -> {
            seenByInner.add(reply);
            return new GateResult.Restored(reply, "R:" + reply);
        };
        final GateFunction gate = ProtectedSpans.gate(Map.of(segment.id(), mask(segment.masked(), HALE)), inner);

        final GateResult result = gate.restore(segment, "⟦g0⟧ відчинив двері.");

        assertThat(seenByInner).containsExactly("Гейл відчинив двері.");
        assertThat(result).isEqualTo(new GateResult.Restored("Гейл відчинив двері.", "R:Гейл відчинив двері."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repliesBreakingTheTokenRule")
    void gate_tokenNotReturnedExactlyOnce_failsAHighGlossaryGateWithTheCount(
            final String name, final String reply, final String expectedNote) {
        final Segment segment = ProtectedSpansFixtures.segment("Hale opened the door.");
        final GateFunction gate = ProtectedSpans.gate(
                Map.of(segment.id(), mask(segment.masked(), HALE)), ProtectedSpansFixtures.PASSTHROUGH);

        final GateResult result = gate.restore(segment, reply);

        assertThat(result).isInstanceOfSatisfying(GateResult.GateFailed.class, failed -> {
            assertThat(failed.finding().kind()).isEqualTo("glossary");
            assertThat(failed.finding().severity()).isEqualTo(Severity.HIGH);
            assertThat(failed.finding().raisedBy()).isEqualTo("locked-term");
            assertThat(failed.finding().note()).isEqualTo(expectedNote);
            assertThat(failed.error().code()).isEqualTo(ErrorCode.validation);
        });
    }

    private static Stream<Arguments> repliesBreakingTheTokenRule() {
        return Stream.of(
                Arguments.of(
                        "the token is missing",
                        "Він відчинив двері.",
                        "The protected token ⟦g0⟧ came back 0 times instead of exactly once."),
                Arguments.of(
                        "the token is repeated",
                        "⟦g0⟧ і ⟦g0⟧ пішли.",
                        "The protected token ⟦g0⟧ came back 2 times instead of exactly once."));
    }

    @Test
    void gate_failure_carriesOnlyTokensInItsDetails() {
        final Segment segment = ProtectedSpansFixtures.segment("Hale opened the door.");
        final GateFunction gate = ProtectedSpans.gate(
                Map.of(segment.id(), mask(segment.masked(), HALE)), ProtectedSpansFixtures.PASSTHROUGH);

        final GateResult result = gate.restore(segment, "Він відчинив двері ⟦g0⟧ ⟦g0⟧.");

        assertThat(result).isInstanceOfSatisfying(GateResult.GateFailed.class, failed -> {
            assertThat(failed.error().details()).contains("⟦g0⟧ ⟦g0⟧").doesNotContain("відчинив", "Гейл");
            assertThat(failed.error().message()).doesNotContain("відчинив", "Гейл");
        });
    }

    @Test
    void gate_segmentWithoutAMask_handsTheReplyUnchangedToTheInnerGate() {
        final Segment segment = ProtectedSpansFixtures.segment("Hale opened the door.");
        final GateFunction gate = ProtectedSpans.gate(Map.of(), ProtectedSpansFixtures.PASSTHROUGH);

        assertThat(gate.restore(segment, "⟦g0⟧ відчинив двері."))
                .isEqualTo(new GateResult.Restored("⟦g0⟧ відчинив двері.", "⟦g0⟧ відчинив двері."));
    }

    @Test
    void gate_innerGateRefusesTheRestoredText_passesItsAnswerThrough() {
        final Segment segment = ProtectedSpansFixtures.segment("Hale opened the door.");
        final GateResult refusal =
                new GateResult.StepError(ua.bookloom.api.AppError.of(ErrorCode.internal, "Gate exploded", "boom"));
        final GateFunction gate =
                ProtectedSpans.gate(Map.of(segment.id(), mask(segment.masked(), HALE)), (given, reply) -> refusal);

        assertThat(gate.restore(segment, "⟦g0⟧ відчинив двері.")).isSameAs(refusal);
    }

    @Test
    void checkRestored_storedTargetMissingTheLockedRendering_failsAHighGlossaryFinding() {
        final Result<String> checked = ProtectedSpans.checkRestored("Хейл кивнув.", mask("Hale nodded.", HALE));

        assertThat(checked.isErr()).isTrue();
        final AppError error = Objects.requireNonNull(checked.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(error.details()).contains("glossary").doesNotContain("Хейл", "Гейл");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("storedTargetsThatKeepTheirSpans")
    void checkRestored_storedTargetHoldingEachSpan_answersItReMasked(
            final String source, final String storedTarget, final String remasked) {
        final Result<String> checked = ProtectedSpans.checkRestored(
                storedTarget, mask(source, HALE, ProtectedSpansFixtures.locked("Baker Street", "Бейкер-стріт")));

        assertThat(checked.data()).isEqualTo(remasked);
    }

    private static Stream<Arguments> storedTargetsThatKeepTheirSpans() {
        return Stream.of(
                Arguments.of("Hale nodded.", "Гейл кивнув.", "⟦g0⟧ кивнув."),
                Arguments.of("Hale saw Hale.", "Гейл бачив Гейл.", "⟦g0⟧ бачив ⟦g1⟧."),
                Arguments.of("Hale left Baker Street.", "Гейл покинув Бейкер-стріт.", "⟦g0⟧ покинув ⟦g1⟧."),
                Arguments.of("No name here.", "Тут немає імені.", "Тут немає імені."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("storedTargetsThatBreakASpan")
    void checkRestored_renderingCountDiffersFromTheSource_fails(
            final String name, final String source, final String storedTarget) {
        assertThat(ProtectedSpans.checkRestored(storedTarget, mask(source, HALE))
                        .isErr())
                .isTrue();
    }

    private static Stream<Arguments> storedTargetsThatBreakASpan() {
        return Stream.of(
                Arguments.of("one of two occurrences inflected", "Hale saw Hale.", "Гейл бачив Гейла."),
                Arguments.of("an extra occurrence", "Hale nodded.", "Гейл кивнув, Гейл пішов."),
                Arguments.of("inside a longer word only", "Hale nodded.", "Гейлові кивнули."));
    }
}
