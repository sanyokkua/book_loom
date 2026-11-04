package ua.bookloom.pipeline.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

class SourceVariantsTest {

    private static final String GATE = "The gate stayed shut until the morning came.";
    private static final String USUAL = "Брама лишалася зачиненою до ранку.";
    private static final String ODD = "Брама була замкнена до світанку.";

    private static Map<String, String> of(final String... pairs) {
        final Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }

    @Test
    void audit_sameSixWordSourceTwoTargets_doubtsMinority() {
        final Map<String, List<QaFinding>> found =
                SourceVariants.find(of("s1", GATE, "s2", GATE, "s3", GATE), of("s1", USUAL, "s2", ODD, "s3", USUAL));

        assertThat(found).containsOnlyKeys("s2");
        assertThat(found.get("s2")).singleElement().satisfies(finding -> {
            assertThat(finding.raisedBy()).isEqualTo("source-variants");
            assertThat(finding.severity()).isEqualTo(Severity.LOW);
        });
    }

    @Test
    void audit_aTie_keepsTheFirstSeenTarget() {
        assertThat(SourceVariants.find(of("s1", GATE, "s2", GATE), of("s1", USUAL, "s2", ODD)))
                .containsOnlyKeys("s2");
    }

    @Test
    void audit_fourWordSource_isNotCompared() {
        assertThat(SourceVariants.find(
                        of("s1", "The gate stayed shut.", "s2", "The gate stayed shut."), of("s1", USUAL, "s2", ODD)))
                .isEmpty();
    }

    @Test
    void audit_targetsDifferingInCaseAndSpacingOnly_noDoubt() {
        assertThat(SourceVariants.find(
                        of("s1", GATE, "s2", "The  gate stayed shut until the morning came!"),
                        of("s1", USUAL, "s2", "брама  лишалася зачиненою до ранку")))
                .isEmpty();
    }
}
