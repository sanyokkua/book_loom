package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;

/** Each dial setting is named by its own Book Brief entry, so a table can head its columns the way the brief does. */
class QualityLabelsTest {

    @ParameterizedTest
    @CsvSource({"FAST, BRIEF_QUALITY_FAST", "BALANCED, BRIEF_QUALITY_BALANCED", "MAX, BRIEF_QUALITY_MAX"})
    void of_dial_isItsBriefEntry(final QualityDial dial, final MessageKey expected) {
        assertThat(QualityLabels.of(dial)).isEqualTo(expected);
    }
}
