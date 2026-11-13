package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeadingNumberTest {

    @Test
    void restore_arabicHeading_prependsFourDot() {
        final Edit edit = LeadingNumber.apply("4. Ночной перевал", "Нічний перевал");

        assertThat(edit.text()).isEqualTo("4. Нічний перевал");
        assertThat(edit.count()).isEqualTo(1);
    }

    @Test
    void restore_romanNumeral_prependsXii() {
        assertThat(LeadingNumber.apply("XII) Возвращение", "Повернення").text()).isEqualTo("XII) Повернення");
    }

    @Test
    void restore_markdownHeading_keepsTheMarkerFirst() {
        assertThat(LeadingNumber.apply("# 4. Ночной перевал", "# Нічний перевал")
                        .text())
                .isEqualTo("# 4. Нічний перевал");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "bare I is the pronoun|I opened it.|Я відчинив це.",
                "target already numbered|4. Ночной перевал|4. Нічний перевал",
                "target numbered differently|4. Ночной перевал|5. Нічний перевал",
                "no number in the source|Ночной перевал|Нічний перевал",
                "four digits are a year|1914. Он ушёл.|Він пішов.",
                "decimal is no heading number|3.5 Итоги|Підсумки",
            })
    void restore_noLostNumber_isUnchanged(final String name, final String source, final String target) {
        assertThat(LeadingNumber.apply(source, target)).isEqualTo(new Edit(target, 0));
    }

    // IF a Latin initial were read as a Roman numeral, THEN «M. Poirot smiled.» would give the target an «M.».
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "M. Poirot smiled.|Пуаро усміхнувся.",
                "C. S. Lewis wrote it.|Це написав Клайв Льюїс.",
                "V. I. Lenin spoke.|Ленін промовляв."
            })
    void restore_latinInitial_unchanged(final String source, final String target) {
        assertThat(LeadingNumber.apply(source, target)).isEqualTo(new Edit(target, 0));
    }

    @Test
    void restore_romanHeading_restored() {
        assertThat(LeadingNumber.apply("IV. The Night Pass", "Нічний перевал").text())
                .isEqualTo("IV. Нічний перевал");
    }

    @Test
    void restore_restoredText_isUnchangedOnASecondPass() {
        final Edit first = LeadingNumber.apply("4. Ночной перевал", "Нічний перевал");

        assertThat(LeadingNumber.apply("4. Ночной перевал", first.text()).count())
                .isZero();
    }
}
