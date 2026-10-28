package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The cases later groups' acceptance tests are written against are labelled in the Russian mini book. */
class MiniRuLabelsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "inflected-form-as-new-name",
                "genitive-with-own-gender",
                "gender-after-run",
                "ch02.md:14",
                "ch02.md:16"
            })
    void labels_laterGroupCases_arePresent(final String idOrDefectClass) {
        final MiniLabels labels = MiniLabels.load(MiniBook.MINI_RU);

        assertThat(Stream.concat(
                        labels.byBlock().keySet().stream(),
                        labels.byBlock().values().stream().map(MiniLabels.Label::defectClass)))
                .contains(idOrDefectClass);
        assertThat(labels.byBlock().values())
                .allSatisfy(l -> assertThat(l.pair()).isEqualTo("ru-uk"));
    }
}
