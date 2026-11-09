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
 * What a rewrite may not lose, as the backward revision's guards held it and the never-worse gate holds it now: no
 * clause, no digit, and for an unmarked paragraph of Max's re-read no more than the rewrite cap of its words.
 */
class NeverWorseRewriteGuardsTest {

    private static final String UK = "uk";

    private static WindowFacts unmarkedMax(final String source) {
        return WindowFacts.of(source, EN, UK, List.of(), null, true);
    }

    // IF a rewrite that drops a clause were kept, THEN a sentence of three clauses would lose its middle one.
    @Test
    void judge_revisionDropsClausePunctuation_rejectsClauses() {
        final String source = "Denny came in, then sat down, and then thought it over here.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Денні зайшов, потім сів, а далі замислився тут."),
                en(source, "Денні зайшов, потім сів а далі замислився тут."),
                facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("clauses"));
    }

    @Test
    void judge_unmarkedMaxRewriteKeepsEveryClause_keeps() {
        final String source = "Denny came in, then sat down, and then thought it over here.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Денні зайшов, потім сів, а далі замислився тут."),
                en(source, "Денні увійшов, потім сів, а далі замислився тут."),
                unmarkedMax(source));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "He said this very quietly and left.|Він сказав це дуже тихо і пішов.|Він сказав це дуже тихо й пішов.|true",
                "He said this very quietly and left.|Він сказав це дуже тихо і пішов.|Вона мовила інакше голосно та вийшла.|false",
                "One two three four five six seven.|Раз два три чотири п'ять шість сім.|Раз два три чотири п'ять шість вісім.|true",
                "One two three four five six seven.|Раз два три чотири п'ять шість сім.|Раз два три чотири інше нове вісім.|false"
            })
    void judge_unmarkedMaxChangedWordShare_isCappedAtAThird(
            final String source, final String current, final String candidate, final boolean kept) {
        final Verdict verdict = NeverWorse.judge(en(source, current), en(source, candidate), unmarkedMax(source));

        assertThat(verdict).isEqualTo(kept ? new Verdict.Keep() : new Verdict.Reject("no-gain"));
    }

    @Test
    void rewriteCap_isThirtyFivePercent() {
        assertThat(TextGuards.REWRITE_CAP).isEqualTo(0.35);
    }

    @Test
    void judge_swapsNumberDigits_rejects() {
        final String source = "He paid 12 coins.";

        final Verdict verdict = NeverWorse.judge(
                en(source, "Він заплатив 12 монет."), en(source, "Він заплатив 21 монет."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Reject("number-lost"));
    }
}
