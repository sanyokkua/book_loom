package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/** A stored finding blocks acceptance when a hard gate or a blocking text check raised it at high severity. */
class BlockingFindingsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"script-purity", "language-identity", "quote-balance", "protocol-leak", "vocative", "placeholder"
            })
    void isBlocking_hardGateAtHighSeverity_isTrue(final String raisedBy) {
        assertThat(BlockingFindings.isBlocking(new QaFinding("language", Severity.HIGH, "x", raisedBy)))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate-word", "spacing", "reviewer", "length"})
    void isBlocking_softCheck_isFalse(final String raisedBy) {
        assertThat(BlockingFindings.isBlocking(new QaFinding("fluency", Severity.HIGH, "x", raisedBy)))
                .isFalse();
    }

    @Test
    void isBlocking_hardGateAtLowSeverity_isFalse() {
        assertThat(BlockingFindings.isBlocking(new QaFinding("language", Severity.LOW, "x", "script-purity")))
                .isFalse();
    }
}
