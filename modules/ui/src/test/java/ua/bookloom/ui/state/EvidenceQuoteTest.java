package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/** Which words of the target a stored finding is about. */
class EvidenceQuoteTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "\"відчинив\" — the verb disagrees|відчинив",
                "\"давай\" could read \"дай\"|давай",
                "\"кафедрахрі\" — not a word; the reviewer suggests \"кафедра\"|кафедрахрі"
            })
    void of_noteOpeningWithAQuote_returnsTheQuotedWords(final String note, final String expected) {
        final QaFinding finding = new QaFinding("language", Severity.MEDIUM, note, "reviewer");

        assertThat(EvidenceQuote.of(finding)).contains(expected);
    }

    @Test
    void of_truncatedExcerpt_dropsTheEllipsis() {
        final QaFinding finding = new QaFinding("echo", Severity.LOW, "\"Gale opened the…\" — left in English", "echo");

        assertThat(EvidenceQuote.of(finding)).contains("Gale opened the");
    }

    @Test
    void of_appliedEdit_returnsTheReplacementThatStandsInTheTarget() {
        final QaFinding finding = new AppliedEdit("gender", "Вона втомився", "Вона втомилася").toFinding();

        assertThat(EvidenceQuote.of(finding)).contains("Вона втомилася");
    }

    @Test
    void of_deletingEdit_returnsNothing() {
        assertThat(EvidenceQuote.of(new AppliedEdit("invented-word", "абра ", "").toFinding()))
                .isEmpty();
    }

    @Test
    void of_noteWithNoQuote_returnsNothing() {
        assertThat(EvidenceQuote.of(new QaFinding("fluency", Severity.LOW, "Reads stiffly", "reviewer")))
                .isEmpty();
    }

    @Test
    void allOf_mixedFindings_keepsOnlyTheQuotesInOrder() {
        final List<QaFinding> findings = List.of(
                new QaFinding("fluency", Severity.LOW, "Reads stiffly", "reviewer"),
                new QaFinding("glossary", Severity.LOW, "\"Гейл\" — locked name", "locked-term"));

        assertThat(EvidenceQuote.allOf(findings)).containsExactly("Гейл");
    }
}
