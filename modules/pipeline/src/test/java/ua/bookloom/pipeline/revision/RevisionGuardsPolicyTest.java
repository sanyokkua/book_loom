package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The consistency pass may not drop a clause, and may not rewrite more than a third of a paragraph. */
class RevisionGuardsPolicyTest {

    // IF a revision that drops a clause were kept, THEN "Боббі зайшов, сів і замислився." would lose its middle (p160).
    @Test
    void violation_revisionDropsClausePunctuation_namesTheClausesRule() {
        assertThat(RevisionGuards.violation(
                        "Боббі зайшов, потім сів, а далі замислився тут.",
                        "Боббі зайшов, потім сів а далі замислився тут.",
                        RevisionGuards.Mode.SAME_COUNTS))
                .contains(RevisionGuards.CLAUSES);
    }

    @Test
    void violation_revisionKeepsEveryClause_isEmpty() {
        assertThat(RevisionGuards.violation(
                        "Боббі зайшов, потім сів, а далі замислився тут.",
                        "Боббі увійшов, потім сів, а далі замислився тут.",
                        RevisionGuards.Mode.SAME_COUNTS))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Він сказав це дуже тихо і пішов.|Він сказав це дуже тихо й пішов.|false",
                "Він сказав це дуже тихо і пішов.|Вона мовила інакше голосно та вийшла.|true",
                "Раз два три чотири п'ять шість сім.|Раз два три чотири п'ять шість вісім.|false",
                "Раз два три чотири п'ять шість сім.|Раз два три чотири інше нове вісім.|true"
            })
    void exceedsRewriteCap_changedWordShare_isCappedAtAThird(
            final String before, final String after, final boolean exceeds) {
        assertThat(RevisionGuards.exceedsRewriteCap(before, after)).isEqualTo(exceeds);
    }

    @Test
    void rewriteCap_isThirtyFivePercent() {
        assertThat(RevisionGuards.REWRITE_CAP).isEqualTo(0.35);
    }
}
