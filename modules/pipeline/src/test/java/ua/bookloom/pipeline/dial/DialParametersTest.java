package ua.bookloom.pipeline.dial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/** The dial's parameter table is the one place chunking, repairs and the judge read the dial from. */
class DialParametersTest {

    @ParameterizedTest
    @CsvSource({"FAST,1,1,false,false,false,8", "BALANCED,2,2,true,false,false,4", "MAX,3,3,true,true,true,2"})
    void of_eachDial_holdsItsRow(
            final QualityDial dial,
            final int preceding,
            final int repairs,
            final boolean judge,
            final boolean backward,
            final boolean summary,
            final int cap) {
        assertThat(DialParameters.of(dial))
                .isEqualTo(new DialParameters(preceding, repairs, judge, backward, summary, cap));
    }

    @ParameterizedTest
    @CsvSource({"FAST", "BALANCED", "MAX"})
    void chunkCap_manualReview_isOne(final QualityDial dial) {
        assertThat(DialParameters.of(dial).chunkCap(ReviewMode.MANUAL)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"FAST,UNATTENDED,8", "BALANCED,ASSISTED,4", "MAX,UNATTENDED,2"})
    void chunkCap_otherModes_isTheDialsCap(final QualityDial dial, final ReviewMode mode, final int cap) {
        assertThat(DialParameters.of(dial).chunkCap(mode)).isEqualTo(cap);
    }
}
