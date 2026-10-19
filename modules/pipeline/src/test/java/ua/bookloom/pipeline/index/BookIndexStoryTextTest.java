package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;

/** Which mentions lie in the story's running text, the measure the name triage counts narration with. */
class BookIndexStoryTextTest {

    static Stream<Arguments> places() {
        return Stream.of(
                Arguments.of(UnitRole.BODY, SegmentKind.PARAGRAPH, "Then we saw Quillon at the gate.", true),
                Arguments.of(UnitRole.BODY, SegmentKind.PARAGRAPH, "Mara said, “We saw Quillon at the gate.”", true),
                Arguments.of(UnitRole.BODY, SegmentKind.HEADING, "Then we saw Quillon at the gate.", false),
                Arguments.of(UnitRole.BODY, SegmentKind.NAV_LABEL, "Then we saw Quillon at the gate.", false),
                Arguments.of(UnitRole.FRONT_MATTER, SegmentKind.PARAGRAPH, "Then we saw Quillon at the gate.", false),
                Arguments.of(UnitRole.BACK_MATTER, SegmentKind.PARAGRAPH, "Then we saw Quillon at the gate.", false),
                Arguments.of(UnitRole.BODY, SegmentKind.PARAGRAPH, "The Quillon Gate", false),
                Arguments.of(
                        UnitRole.BODY, SegmentKind.PARAGRAPH, "First published in Quillon by Harbour Press.", false));
    }

    // IF a name is written in a heading, a contents entry, front or back matter, a title-line paragraph or a printing
    // note, THEN its mention is not in the story's text; speech and plain narration are.
    @ParameterizedTest(name = "{0} {1}: {2} -> {3}")
    @MethodSource("places")
    void isStoryText_mentionByPlace_isTrueOnlyForRunningStoryText(
            final UnitRole role, final SegmentKind kind, final String line, final boolean expected) {
        final BookIndex index = BookIndex.of(IndexTestBooks.document(IndexTestBooks.unit("u", role, kind, line)));

        final List<Mention> found = index.mentions(List.of("Quillon"), Case.SENSITIVE);

        assertThat(found).hasSize(1);
        assertThat(index.isStoryText(found.getFirst())).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("places")
    void isStoryText_auxiliaryUnit_isNeverStoryText(
            final UnitRole role, final SegmentKind kind, final String line, final boolean ignored) {
        final BookIndex index = BookIndex.of(
                IndexTestBooks.document(IndexTestBooks.unit(Unit.AUXILIARY_ID, UnitRole.BODY, kind, line)));

        assertThat(index.isStoryText(
                        index.mentions(List.of("Quillon"), Case.SENSITIVE).getFirst()))
                .isFalse();
    }
}
