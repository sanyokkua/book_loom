package ua.bookloom.pipeline.dial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.QualityDial;

/** The window reads the table a run reads: one row per dial, the same record. */
class DialParameterProfilesTest {

    @ParameterizedTest
    @EnumSource(QualityDial.class)
    void of_eachDial_isDialParametersRow(final QualityDial dial) {
        assertThat(new DialParameterProfiles().of(dial)).isEqualTo(DialParameters.of(dial));
    }
}
