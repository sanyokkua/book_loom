package ua.bookloom.pipeline.dial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;

/** The dial's parameter table is the one place chunking, repairs and the reviewer read the dial from. */
class DialParametersTest {

    @ParameterizedTest
    @CsvSource({"FAST,1,MARKS_ONLY,0,false,false", "BALANCED,2,ONE,1,false,false", "MAX,3,ONE,2,true,true"})
    void of_eachDial_holdsItsRow(
            final QualityDial dial,
            final int preceding,
            final RepairMode repairs,
            final int reviewPasses,
            final boolean backward,
            final boolean summary) {
        assertThat(DialParameters.of(dial))
                .isEqualTo(new DialParameters(preceding, repairs, reviewPasses, backward, summary));
    }
}
