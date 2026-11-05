package ua.bookloom.pipeline.dial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;

/** The dial's parameter table is the one place chunking, repairs and the reviewer read the dial from. */
class DialParametersTest {

    @ParameterizedTest
    @CsvSource({"FAST,MARKS_ONLY,0,false", "BALANCED,ONE,1,false", "MAX,ONE,2,true"})
    void of_eachDial_holdsItsRow(
            final QualityDial dial, final RepairMode repairs, final int reviewPasses, final boolean backward) {
        assertThat(DialParameters.of(dial)).isEqualTo(new DialParameters(repairs, reviewPasses, backward));
    }
}
