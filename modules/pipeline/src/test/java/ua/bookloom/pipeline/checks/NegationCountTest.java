package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NegationCountTest {

    @Test
    void of_ukTwoNegations_countsTwo() {
        assertThat(NegationCount.of("Він не знав, що ніхто не прийде.", "uk")).isEqualTo(3);
    }

    @Test
    void of_ukTwoNegationWords_countsTwo() {
        assertThat(NegationCount.of("Він ніколи не знав.", "uk")).isEqualTo(2);
    }

    @Test
    void of_negationInsideAnotherWord_isNotCounted() {
        assertThat(NegationCount.of("Нехай нічого", "uk")).isEqualTo(1);
    }

    @Test
    void of_languageWithNoNegationList_countsZero() {
        assertThat(NegationCount.of("He did not know that nobody would come.", "en"))
                .isZero();
    }
}
