package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The proofread summary line states both counts in both bundles, with Ukrainian plural forms. */
class ExportReportLinesProofreadTest {

    private final Messages english = UiTestInjector.create(Locale.ENGLISH).getInstance(Messages.class);
    private final Messages ukrainian =
            UiTestInjector.create(Locale.forLanguageTag("uk")).getInstance(Messages.class);

    @Test
    void proofreadLine_twelveReReadThreeWaiting_readsBothCounts() {
        assertThat(ExportReportLines.proofreadLine(english, new ProofreadSummary(12, 3)))
                .isEqualTo("Proofread: 12 segments re-read, 3 waiting for you");
    }

    @Test
    void proofreadLine_oneReReadNoneWaiting_usesSingularAndShowsZero() {
        assertThat(ExportReportLines.proofreadLine(english, new ProofreadSummary(1, 0)))
                .isEqualTo("Proofread: 1 segment re-read, 0 waiting for you");
    }

    @ParameterizedTest(name = "{0} re-read in Ukrainian")
    @CsvSource({
        "1, перечитано 1 сегмент",
        "2, перечитано 2 сегменти",
        "5, перечитано 5 сегментів",
        "21, перечитано 21 сегмент",
    })
    void proofreadLine_ukrainianCounts_useTheirPluralForm(final int reRead, final String start) {
        assertThat(ExportReportLines.proofreadLine(ukrainian, new ProofreadSummary(reRead, 3)))
                .startsWith("Вичитка: " + start)
                .endsWith("чекають на вас: 3");
    }
}
