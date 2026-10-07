package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A model's claimed pair counts only when the item's own texts prove it. */
class TermMappingVerifierTest {

    private static final List<String> KEY_TERMS = List.of("master", "imp", "Mr");
    private static final String SOURCE = "The master spoke to the imp.";

    private static List<TermMappingVerifier.Pair> verify(
            final Map<String, String> claimed, final String source, final String target) {
        return TermMappingVerifier.verify(claimed, KEY_TERMS, source, target);
    }

    @Test
    void verify_renderingWrittenInTheTarget_isKept() {
        final var pairs = verify(Map.of("master", "господар"), SOURCE, "Господар заговорив до біса.");

        assertThat(pairs).containsExactly(new TermMappingVerifier.Pair("master", "господар"));
    }

    @Test
    void verify_renderingNeverWritten_isDropped() {
        assertThat(verify(Map.of("master", "учитель"), SOURCE, "Господар заговорив до біса."))
                .isEmpty();
    }

    @Test
    void verify_termTheSourceLacks_isDropped() {
        assertThat(verify(Map.of("master", "господар"), "The imp spoke.", "Господар заговорив."))
                .isEmpty();
    }

    @Test
    void verify_termOffTheClosedList_isDropped() {
        assertThat(verify(Map.of("harbour", "гавань"), "The harbour slept.", "Гавань спала."))
                .isEmpty();
    }

    // The target inflects a rendering by the sentence: the stem is what must be there.
    @ParameterizedTest(name = "[{index}] {0} in «{1}»")
    @CsvSource({
        "господар,Він вклонився господарю.",
        "господар,Господаря не було вдома.",
        "біс,Вона побачила біса.",
        "учитель,Вчителя там не було. Учителя кликали.",
        "'старий господар',Старого господаря не було вдома."
    })
    void verify_inflectedRendering_isAccepted(final String rendering, final String target) {
        final var pairs = verify(Map.of("master", rendering), SOURCE, target);

        assertThat(pairs).hasSize(1);
    }

    @Test
    void verify_multiwordRenderingWithOneWordMissing_isDropped() {
        assertThat(verify(Map.of("master", "старий господар"), SOURCE, "Господар заговорив до біса."))
                .isEmpty();
    }

    @Test
    void verify_unrelatedLongerWordSharingAShortStem_isDropped() {
        assertThat(verify(Map.of("imp", "біс"), "The imp spoke.", "Бісівська сила заговорила."))
                .isEmpty();
    }

    @Test
    void verify_termInAnotherCaseAndPlural_matchesTheSourceAndNamesTheListedSpelling() {
        final var pairs = verify(Map.of("MASTERS", "господар"), "The masters spoke.", "Господарі заговорили.");

        assertThat(pairs).isEmpty();
        assertThat(verify(Map.of("mr", "пан"), "Mr Hale spoke.", "Пан Хейл заговорив."))
                .containsExactly(new TermMappingVerifier.Pair("Mr", "пан"));
    }

    @Test
    void verify_blankRendering_isDropped() {
        assertThat(verify(Map.of("master", "  "), SOURCE, "Господар заговорив."))
                .isEmpty();
    }

    @Test
    void verify_hyphenatedTargetWord_holdsEachPart() {
        final var pairs = TermMappingVerifier.verify(
                Map.of("Bartimaeus", "Бартімей"), List.of("Bartimaeus"), "B-Bartimaeus came.", "Б-Бартімей прийшов.");

        assertThat(pairs).containsExactly(new TermMappingVerifier.Pair("Bartimaeus", "Бартімей"));
    }

    @ParameterizedTest
    @CsvSource({"Остап,Остаточно він пішов.", "Тарас,Тарасовими були всі."})
    void verify_longUnrelatedWordSharingTheStem_isDroppedWithAlternationData(
            final String rendering, final String target) {
        final var pairs = TermMappingVerifier.verify(
                Map.of("Name", rendering), List.of("Name"), "Name came.", target, List.of("гзж", "кцч", "хсш"));

        assertThat(pairs).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"Лондон,Лондонського поліцейського викликали.", "Прага,Празькі газети надійшли."})
    void verify_derivedFormOfALongStemOrWithinTheBound_isKept(final String rendering, final String target) {
        final var pairs = TermMappingVerifier.verify(
                Map.of("Name", rendering), List.of("Name"), "Name came.", target, List.of("гзж", "кцч", "хсш"));

        assertThat(pairs).hasSize(1);
    }
}
