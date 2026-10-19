package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/** The one badge a flagged row carries: its main finding, chosen by severity and then by how the person reads it. */
class FindingBadgeTest {

    private static QaFinding finding(final String kind, final Severity severity, final String raisedBy) {
        return new QaFinding(kind, severity, "A note about " + kind, raisedBy);
    }

    // IF a language finding lost a tie to an omission, THEN a wrong-language segment would read as a lesser defect.
    @Test
    void of_mediumOmissionAndMediumLanguage_isWrongLanguage() {
        final var badge = FindingBadge.of(
                List.of(finding("omission", Severity.MEDIUM, "script"), finding("language", Severity.MEDIUM, "script")),
                null);

        assertThat(badge).contains(FindingBadge.WRONG_LANGUAGE);
    }

    // IF severity did not come first, THEN a mild name finding would hide a serious omission.
    @Test
    void of_highOmissionAndMediumGlossary_isOmission() {
        final var badge = FindingBadge.of(
                List.of(finding("glossary", Severity.MEDIUM, "glossary"), finding("omission", Severity.HIGH, "judge")),
                null);

        assertThat(badge).contains(FindingBadge.OMISSION);
    }

    @Test
    void of_singleGlossaryFinding_isName() {
        assertThat(FindingBadge.of(List.of(finding("glossary", Severity.LOW, "glossary")), null))
                .contains(FindingBadge.NAME);
    }

    // The judge writes tag where the checks write markup; both read as a low score.
    @Test
    void of_tagFindingRaisedByJudge_isLowScore() {
        assertThat(FindingBadge.of(List.of(finding("tag", Severity.HIGH, "judge")), 0.4))
                .contains(FindingBadge.LOW_SCORE);
    }

    @Test
    void of_noFindingAndJudgeScore058_isLowScore() {
        assertThat(FindingBadge.of(List.of(), 0.58)).contains(FindingBadge.LOW_SCORE);
    }

    // The judge's kinds are model-written, so their letter case cannot be trusted.
    @Test
    void of_glossaryWrittenInCapitalsByTheJudge_isStillName() {
        assertThat(FindingBadge.of(List.of(finding("Glossary", Severity.MEDIUM, "judge")), null))
                .contains(FindingBadge.NAME);
    }

    @Test
    void of_noFindingAndNoScore_hasNoBadge() {
        assertThat(FindingBadge.of(List.of(), null)).isEmpty();
    }
}
