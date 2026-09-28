package ua.bookloom.document.split;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** {@link IcuSentenceSplitter}: sentence boundaries that respect placeholder tokens, and pieces that rejoin exactly. */
class IcuSentenceSplitterTest {

    private static final PlaceholderPair PAIR = new PlaceholderPair("⟦g0⟧", "⟦g1⟧", null);
    private static final String NO_PUNCTUATION = "word ".repeat(1_200);

    private static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(
                        "Call me Ishmael. Some years ago I went to sea. It was cold.",
                        "en",
                        List.of(),
                        List.of("Call me Ishmael. ", "Some years ago I went to sea. ", "It was cold.")),
                Arguments.of("Він пішов. Вона лишилася.", "uk", List.of(), List.of("Він пішов. ", "Вона лишилася.")),
                Arguments.of(
                        "⟦g0⟧He left. She stayed.⟦g1⟧ Night fell.",
                        "en",
                        List.of(PAIR),
                        List.of("⟦g0⟧He left. She stayed.⟦g1⟧ ", "Night fell.")),
                Arguments.of(
                        "He left. ⟦g0⟧She⟦g1⟧ stayed. Night fell.",
                        "en",
                        List.of(PAIR),
                        List.of("He left. ", "⟦g0⟧She⟦g1⟧ stayed. ", "Night fell.")),
                Arguments.of(
                        "He left.⟦g0⟧ She stayed.⟦g1⟧ Night fell.",
                        "en",
                        List.of(PAIR),
                        List.of("He left.", "⟦g0⟧ She stayed.⟦g1⟧ ", "Night fell.")),
                Arguments.of("⟦g0⟧One. Two. Three.⟦g1⟧", "en", List.of(PAIR), List.of("⟦g0⟧One. Two. Three.⟦g1⟧")),
                Arguments.of(NO_PUNCTUATION, "en", List.of(), List.of(NO_PUNCTUATION)));
    }

    // WHEN a masked text is split, THEN the pieces are cut at sentence ends outside every pair, and they rejoin to
    // the input exactly, whitespace included.
    @ParameterizedTest
    @MethodSource("cases")
    void split_maskedText_cutsAtSentenceEndsAndRejoinsExactly(
            String masked, String language, List<PlaceholderPair> pairs, List<String> expected) {
        final List<String> pieces = new IcuSentenceSplitter()
                .split(masked, segmentWith(pairs), language)
                .data();

        assertThat(pieces).containsExactlyElementsOf(expected);
        assertThat(String.join("", pieces)).isEqualTo(masked);
    }

    // WHEN the splitter is asked for a text with no usable boundary, THEN it answers the whole text as one piece.
    @Test
    void split_emptyText_returnsItAsOnePiece() {
        assertThat(new IcuSentenceSplitter()
                        .split("", segmentWith(List.of()), "en")
                        .data())
                .containsExactly("");
    }

    private static Segment segmentWith(List<PlaceholderPair> pairs) {
        return new Segment(
                "u:0",
                "u",
                0,
                SegmentKind.PARAGRAPH,
                "x",
                "x",
                Map.of(),
                "hash",
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
