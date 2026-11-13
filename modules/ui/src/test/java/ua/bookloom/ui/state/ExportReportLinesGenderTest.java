package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The export's two gender notices in both bundles: a missing gender to set and a suggested one to confirm. */
class ExportReportLinesGenderTest {

    private static final ProofreadSummary SPLIT =
            new ProofreadSummary(12, 7, List.of(new GenderWait("Ember", 5, true), new GenderWait("Dane", 2)), 5, 2);

    private final Messages english = UiTestInjector.create(Locale.ENGLISH).getInstance(Messages.class);
    private final Messages ukrainian =
            UiTestInjector.create(Locale.forLanguageTag("uk")).getInstance(Messages.class);

    @Test
    void english_splitSummary_wordsSetAndConfirmApart() {
        assertThat(ExportReportLines.awaitingGender(english, SPLIT))
                .hasValue("2 segments wait on a character's gender: Dane ×2. Set it in Names & style; the next export"
                        + " re-renders them.");
        assertThat(ExportReportLines.suggestedGender(english, SPLIT))
                .hasValue("5 segments wait on a suggested gender nobody has confirmed: Ember ×5. Confirm it in Names"
                        + " & style; the next export re-renders them.");
    }

    @Test
    void ukrainian_splitSummary_wordsSetAndConfirmApartWithPlurals() {
        assertThat(ExportReportLines.awaitingGender(ukrainian, SPLIT))
                .hasValueSatisfying(
                        line -> assertThat(line).startsWith("2 сегменти чекають на стать персонажа: Dane ×2."));
        assertThat(ExportReportLines.suggestedGender(ukrainian, SPLIT))
                .hasValueSatisfying(line -> assertThat(line)
                        .startsWith("5 сегментів чекають на припущену стать, яку ніхто не підтвердив: Ember ×5."));
    }

    @Test
    void lines_noSuggestedWait_haveNoSuggestedNotice() {
        final ProofreadSummary unknownOnly = new ProofreadSummary(12, 2, List.of(new GenderWait("Dane", 2)), 0, 2);

        assertThat(ExportReportLines.suggestedGender(english, unknownOnly)).isEmpty();
        assertThat(ExportReportLines.awaitingGender(english, unknownOnly)).isPresent();
    }
}
