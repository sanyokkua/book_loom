package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.qa.NeverWorseTest.EN;
import static ua.bookloom.pipeline.qa.NeverWorseTest.en;
import static ua.bookloom.pipeline.qa.NeverWorseTest.facts;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The deterministic guards the per-chunk reviewer applied to its edits, now vetoes of {@link NeverWorse} for every text
 * the program changes (design D14): a doubled word, a changed number, a lower-case sentence start, a new capital in the
 * middle of a sentence, a letter of another script inside a word.
 */
class NeverWorseEditGuardsTest {

    @Test
    void judge_doublesAWord_rejects() {
        final String source = "He opened the door.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він відчинив двері."), en(source, "Він відчинив відчинив двері."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("doubled-word"));
    }

    @Test
    void judge_changesNumber12To21_rejects() {
        final String source = "He paid 12 coins.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він заплатив 12 монет."), en(source, "Він заплатив 21 монету."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("number-lost"));
    }

    @Test
    void judge_lowersASentenceStart_rejects() {
        final String source = "He left. She stayed behind.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він пішов. Вона лишилась."), en(source, "Він пішов. вона лишилась."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("sentence-case"));
    }

    @Test
    void judge_raisesAMidSentenceCapitalNoSourceNameHolds_rejects() {
        final String source = "He walked home.";

        final Verdict verdict =
                NeverWorse.judge(en(source, "Він пішов додому."), en(source, "Він пішов Додому."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("mid-capital"));
    }

    @Test
    void judge_capitalisesNamesTheSourceHolds_isNoMidCapitalVeto() {
        final String source = "He met Anna Smith.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він зустрів анну сміт."), en(source, "Він зустрів Анну Сміт."), facts(source, EN));

        assertThat(verdict).isNotEqualTo(new Verdict.Reject("mid-capital"));
    }

    @Test
    void judge_capitalisesAFormalAddressForm_isNoMidCapitalVeto() {
        final String source = "Tell me, do you know the way?";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Скажіть, чи ви знаєте дорогу?"),
                en(source, "Скажіть, чи Ви знаєте дорогу?"),
                facts(source, EN));

        assertThat(verdict).isNotEqualTo(new Verdict.Reject("mid-capital"));
    }

    @Test
    void judge_capitalisedNounsInATargetThatCapitalisesThem_isNoMidCapitalVeto() {
        final String source = "He opened the door in the house.";
        final WindowFacts german = WindowFacts.of(source, EN, "de", List.of(), null, false);

        final Verdict verdict = NeverWorse.judge(
                en(source, "Er öffnete die tür im haus."), en(source, "Er öffnete die Tür im Haus."), german);

        assertThat(verdict).isNotEqualTo(new Verdict.Reject("mid-capital"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"Він пішов додому.|Він пішов дoму.", "Вона сказала правду.|Вона сказала пpавду."})
    void judge_latinLetterInsideCyrillicWord_rejects(final String current, final String candidate) {
        final String source = "He went home.";

        final Verdict verdict = NeverWorse.judge(en(source, current), en(source, candidate), facts(source, EN));

        assertThat(verdict).isInstanceOf(Verdict.Reject.class);
    }
}
