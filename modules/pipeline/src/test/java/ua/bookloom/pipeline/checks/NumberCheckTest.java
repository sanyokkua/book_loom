package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every multi-digit number of the source is in the target, in whatever grouping the target language uses. */
class NumberCheckTest {

    @Test
    void find_digitDropped_flagsTheChangedNumberSoftly() {
        final List<CheckFinding> found =
                NumberCheck.find("The vault held 408 coins and 12 bars.", "У сховищі було 48 монет і 12 зливків.");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().kind()).isEqualTo(FindingKind.NUMBER_CHANGED);
        assertThat(found.getFirst().blocking()).isFalse();
        assertThat(found.getFirst().span().text()).isEqualTo("48");
        assertThat(found.getFirst().explanation()).contains("408").contains("48");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "She paid 2,500 credits.|Вона заплатила 2500 кредитів.",
                "She paid 2 500 credits.|Вона заплатила 2,500 кредитів.",
                "At 6:45 she left.|О 6:45 вона пішла.",
                "It weighs 3.5 kilos.|Це важить 3,5 кілограма.",
                "In 1973 it began.|У 1973 році це почалося.",
                "Room 7 was empty.|Кімната сім була порожня.",
                "There were 12 bars.|Було дванадцять зливків."
            })
    void find_sameNumberInAnyGroupingOrSpelledOut_isClean(final String source, final String target) {
        assertThat(NumberCheck.find(source, target)).isEmpty();
    }

    @Test
    void find_timeChanged_isFlagged() {
        assertThat(NumberCheck.find("At 6:45 she left.", "О 6:15 вона пішла."))
                .hasSize(1)
                .allSatisfy(finding -> assertThat(finding.explanation()).contains("6:45"));
    }

    @Test
    void find_longNumberLeftOutWithNoOtherDigit_isFlagged() {
        assertThat(NumberCheck.find("The vault held 408 coins.", "У сховищі було багато монет."))
                .hasSize(1);
    }

    @Test
    void find_sourceWithoutNumbers_isClean() {
        assertThat(NumberCheck.find("The vault was empty.", "Сховище було порожнє 5 хвилин."))
                .isEmpty();
    }
}
