package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/** {@link CheckLevels#of}: a stored finding's level comes from the check that raised it and the severity it carries. */
class CheckLevelsTest {

    @Test
    void of_numberFindingAtMediumSeverity_isRepair() {
        assertThat(CheckLevels.of(new QaFinding("meaning", Severity.MEDIUM, "x", "number")))
                .isEqualTo(CheckLevel.REPAIR);
    }

    // A failed soft check with a confidence weight is raised at medium and blocks (the soft check failed outright).
    @Test
    void of_lengthMedium_isBlock() {
        assertThat(CheckLevels.of(new QaFinding("omission", Severity.MEDIUM, "x", "length")))
                .isEqualTo(CheckLevel.BLOCK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reviewer", "reviewer-edit", "audit:alphabet", "reply"})
    void of_unknownRaiser_isNote(final String raisedBy) {
        assertThat(CheckLevels.of(new QaFinding("meaning", Severity.HIGH, "x", raisedBy)))
                .isEqualTo(CheckLevel.NOTE);
    }

    @ParameterizedTest
    @CsvSource({
        "alphabet, HIGH, BLOCK",
        "placeholder, HIGH, BLOCK",
        "gender, HIGH, REPAIR",
        "gender, MEDIUM, REPAIR",
        "address-mix, MEDIUM, REPAIR",
        "spacing, HIGH, NOTE",
        "placeholder, LOW, NOTE",
        "echo, LOW, NOTE",
        "alphabet, MEDIUM, REPAIR"
    })
    void of_raiserAndSeverity_givesTheLevel(final String raisedBy, final Severity severity, final CheckLevel expected) {
        assertThat(CheckLevels.of(new QaFinding("language", severity, "x", raisedBy)))
                .isEqualTo(expected);
    }
}
