package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.document.mask.GateRule;
import ua.bookloom.document.mask.PlaceholderGate;

/**
 * The order rules the gate applies after the multiset matches: a pair opens before it closes and nests properly, a
 * pair that held text still holds text, and a line break keeps its innermost pair (task 5.8).
 */
class UnmaskPairOrderTest {

    private static PlaceholderPair pair(int open, int close) {
        return new PlaceholderPair("⟦g" + open + "⟧", "⟦g" + close + "⟧", null);
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("⟦g0⟧old⟦g1⟧", "⟦g1⟧OLD⟦g0⟧", List.of(pair(0, 1)), List.of(), GateRule.PAIR_ORDER),
                Arguments.of(
                        "⟦g0⟧A⟦g1⟧ and ⟦g2⟧B⟦g3⟧",
                        "⟦g2⟧Б⟦g3⟧ і ⟦g0⟧А⟦g1⟧",
                        List.of(pair(0, 1), pair(2, 3)),
                        List.of(),
                        null),
                Arguments.of(
                        "⟦g0⟧a ⟦g1⟧b⟦g2⟧ c⟦g3⟧",
                        "⟦g0⟧а ⟦g1⟧б⟦g3⟧ в⟦g2⟧",
                        List.of(pair(0, 3), pair(1, 2)),
                        List.of(),
                        GateRule.PAIR_ORDER),
                Arguments.of("⟦g0⟧old⟦g1⟧ door ⟦g2⟧", "⟦g0⟧старі ⟦g2⟧⟦g1⟧ двері", List.of(pair(0, 1)), List.of(), null),
                Arguments.of("⟦g0⟧old⟦g1⟧", "⟦g0⟧⟦g1⟧OLD", List.of(pair(0, 1)), List.of(), GateRule.EMPTIED_PAIR),
                Arguments.of("a⟦g0⟧ ⟦g1⟧b", "а⟦g0⟧⟦g1⟧б", List.of(pair(0, 1)), List.of(), null),
                Arguments.of(
                        "x⟦g0⟧one⟦g1⟧two⟦g2⟧y",
                        "x⟦g0⟧один два⟦g2⟧⟦g1⟧y",
                        List.of(pair(0, 2)),
                        List.of("⟦g1⟧"),
                        GateRule.LINE_BREAK),
                Arguments.of(
                        "x⟦g0⟧one⟦g1⟧two⟦g2⟧y", "x⟦g0⟧один два⟦g1⟧⟦g2⟧y", List.of(pair(0, 2)), List.of("⟦g1⟧"), null),
                Arguments.of("See ⟦g0⟧ and ⟦g1⟧.", "Дивіться ⟦g1⟧ і ⟦g0⟧.", List.of(), List.of(), null));
    }

    // WHEN the target keeps the multiset but breaks an order rule, THEN the gate names that rule; otherwise it passes.
    @ParameterizedTest
    @MethodSource("cases")
    void compare_targetOrder_namesTheRuleBrokenOrPasses(
            String masked, String target, List<PlaceholderPair> pairs, List<String> lineBreaks, GateRule expected) {
        assertThat(PlaceholderGate.compare(masked, target, pairs, lineBreaks).failedRule())
                .isEqualTo(expected);
    }

    // WHEN a swapped pair reaches unmask through the port, THEN it is a validation error and nothing is restored.
    @Test
    void unmask_swappedPair_returnsValidationErrorWithNoRestoredText() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧", List.of(pair(0, 1)));

        final Result<String> result = DocumentServices.newService().unmask(BookFormat.EPUB, segment, "⟦g1⟧OLD⟦g0⟧");

        assertThat(result.data()).isNull();
        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    private static Segment segment(String masked, List<PlaceholderPair> pairs) {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "<em>");
        placeholders.put("g1", "</em>");
        return new Segment(
                "seg-1",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                placeholders,
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                pairs,
                List.of());
    }
}
