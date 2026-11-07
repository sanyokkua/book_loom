package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.TooltipProbe;

/**
 * "Worth a second look" explains itself on hover where its count is shown — on the Translating screen's outcome card
 * and on the desk's filter — as the accepted segments the audit or the Proofread stage still doubts; the export's last
 * check is gone, so neither names it.
 */
class SecondLookHoverTest extends ReviewDeskTestBase {

    // IF the count named a last check that no longer exists, THEN a person would look for a step the export no longer
    // takes.
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            en | the audit or the Proofread stage still doubts | last check
            uk | аудит або вичитка ще ставлять під сумнів | остання перевірка
            """)
    void outcomeCardAndFilter_explainAuditOrProofread(final String language, final String says, final String gone)
            throws Exception {
        useLocale(Locale.forLanguageTag(language));
        openDeskWith(ReviewDeskFixtures.fourToReview(), ReviewDeskFixtures.worthASecondLook());
        chooseFilter(ReviewFilter.SUSPICIOUS);

        final String card = fx(() -> TooltipProbe.tipText(required("translating-count-second-look")));
        final String filter = fx(() -> TooltipProbe.tipText(filterBox().getButtonCell()));

        assertThat(card).contains(says).doesNotContain(gone);
        assertThat(filter).contains(says).doesNotContain(gone);
    }
}
