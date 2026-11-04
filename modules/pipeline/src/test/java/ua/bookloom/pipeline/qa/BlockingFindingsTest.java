package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/** A stored finding blocks acceptance when its level ({@link CheckLevels#of}) is {@link CheckLevel#BLOCK}. */
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
    @ValueSource(strings = {"duplicate-word", "spacing", "reviewer", "gender"})
    void isBlocking_softCheck_isFalse(final String raisedBy) {
        assertThat(BlockingFindings.isBlocking(new QaFinding("fluency", Severity.HIGH, "x", raisedBy)))
                .isFalse();
    }

    @Test
    void isBlocking_hardGateAtLowSeverity_isFalse() {
        assertThat(BlockingFindings.isBlocking(new QaFinding("language", Severity.LOW, "x", "script-purity")))
                .isFalse();
    }

    // A failed length check is raised at medium severity and no hard gate raised it, yet its level is BLOCK.
    @Test
    void blocking_readsTheLevelNotHardGate() {
        assertThat(BlockingFindings.isBlocking(new QaFinding("omission", Severity.MEDIUM, "x", "length")))
                .isTrue();
    }
}
