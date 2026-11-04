package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which answers copy an example: a run of four words of an example's target, a mark standing for any one word. */
class EchoGateRunTest {

    private static final ExampleChoice PAIR = new ExampleChoice(
            ExampleChoice.Kind.PAIR,
            "<Items>\n<s id=\"1\">— Погоди, ⟦g0⟧! — крикнула она.</s>\n</Items>\n"
                    + "Reply: {\"items\":[{\"id\":\"1\",\"target\":\"— Стривай, ⟦g0⟧! — гукнула вона.\"}]}");
    private static final ExampleChoice ILLUSTRATION =
            new ExampleChoice(ExampleChoice.Kind.TARGET, "Вечір тихо спускався на старе місто над рікою.");

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "— Стривай, Гордєю! — гукнула вона.|— Тише, Зотов! — сказал он.|true",
                "— Тихше, Зотове! — сказав він.|— Тише, Зотов! — сказал он.|false",
                "Вона сказала: стривай, Гордію, гукнула вона.|She said: стривай, Гордію, гукнула вона.|false",
                "Стривай гукнула|— Тише! — сказал он.|false"
            })
    void copies_pairExample_markMatchesAnyWordAndTheSourceExcuses(
            final String target, final String source, final boolean copies) {
        assertThat(EchoGate.copies(target, source, PAIR)).isEqualTo(copies);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Вечір тихо спускався на площу.|The evening fell on the square.|true",
                "Вечір прийшов.|The evening came.|false"
            })
    void copies_targetIllustration_comparesItsWholeText(
            final String target, final String source, final boolean copies) {
        assertThat(EchoGate.copies(target, source, ILLUSTRATION)).isEqualTo(copies);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"— Стривай, Гордєю! — гукнула вона.|— Тише, Зотов! — сказал он."})
    void copies_noExamples_neverCopies(final String target, final String source) {
        assertThat(EchoGate.copies(target, source, ExampleChoice.NONE)).isFalse();
    }
}
