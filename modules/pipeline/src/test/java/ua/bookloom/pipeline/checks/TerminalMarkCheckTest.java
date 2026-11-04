package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.qa.CheckLevel;

/** The class of a paragraph's last mark must survive translation; quotes around it are no difference. */
class TerminalMarkCheckTest {

    @Test
    void check_questionBecameFullStop_repair() {
        final Optional<CheckFinding> found = TerminalMarkCheck.find("Where is the key?", "Де ключ.", UnitRole.BODY);

        assertThat(found).hasValueSatisfying(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.TERMINAL_MARK);
            assertThat(finding.level()).isEqualTo(CheckLevel.REPAIR);
            assertThat(finding.explanation()).contains("question mark").contains("full stop");
        });
    }

    @Test
    void check_ellipsisAgainstFullStop_repair() {
        assertThat(TerminalMarkCheck.find("He waited…", "Він чекав.", UnitRole.BODY))
                .isPresent();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "“Where is the key?”|«Де ключ?»",
                "He waited...|Він чекав…",
                "Stop!|Стій!",
                "Really?!|Справді?",
                "He said (quietly.)|Він сказав (тихо.)",
                "Chapter one|Розділ перший",
                "He said:|Він сказав:",
                "Where is the key?|Де ключ"
            })
    void check_sameClassOrNoMark_isClean(final String source, final String target) {
        assertThat(TerminalMarkCheck.find(source, target, UnitRole.BODY)).isEmpty();
    }

    @Test
    void check_aHeading_isNotJudged() {
        assertThat(TerminalMarkCheck.find("Where?", "Де.", UnitRole.FRONT_MATTER))
                .isEmpty();
    }
}
