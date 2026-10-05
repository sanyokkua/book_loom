package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppliedEditTest {

    @Test
    void from_findingMadeByToFinding_readsTheSameEditBack() {
        final AppliedEdit edit = new AppliedEdit("gender", "він пішла", "вона пішла");

        assertThat(AppliedEdit.from(edit.toFinding())).contains(edit);
    }

    @Test
    void toFinding_anyEdit_isALowFindingSoItNeverBlocks() {
        final QaFinding finding = new AppliedEdit("omission", "a", "a b").toFinding();

        assertThat(finding.severity()).isEqualTo(Severity.LOW);
        assertThat(finding.raisedBy()).isEqualTo("reviewer-edit");
    }

    @Test
    void from_deletionEdit_keepsAnEmptyReplacement() {
        final AppliedEdit edit = new AppliedEdit("invented-word", "абракадабра ", "");

        assertThat(AppliedEdit.from(edit.toFinding())).contains(edit);
    }

    @Test
    void from_findingOfAnotherCheck_isEmpty() {
        assertThat(AppliedEdit.from(new QaFinding("script", Severity.LOW, "x␞y", "script-purity")))
                .isEmpty();
    }
}
