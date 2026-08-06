package ua.bookloom.archtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Counts the rules, by name, against the eight the specification enumerates.
 *
 * <p>A rule dropped from the suite is invisible by inspection: the build stays green, the report shrinks by a few
 * lines nobody reads, and the boundary it guarded silently becomes a convention again. The same is true of a rule
 * renamed away from its spec name — it still runs, but nobody looking for
 * {@code 02_MODULES_AND_LAYERING.md#archunit-rules} rule 5 can find it.
 *
 * <p>Covers task 4.10 of the {@code bootstrap-gradle-and-quality-toolchain} change.
 */
class RuleSuiteCompletenessTest {

    /**
     * The eight rule names of {@code 02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules}, in the order that
     * section lists them. Hard-coded on purpose: this list is a transcription of the specification, so deriving it
     * from the code it checks would make the test a tautology.
     */
    private static final List<String> SPEC_RULE_NAMES = List.of(
            "fx-free-core",
            "dependency-direction",
            "ports-not-concretes",
            "no-http-in-core-except-llm",
            "no-sql-in-core-except-persistence",
            "api-is-framework-free",
            "records-first",
            "bootstrap-no-static-logger");

    @Test
    void suite_containsExactlyTheEightRulesTheSpecificationEnumerates() {
        assertThat(RuleSuite.byName().keySet())
                .as("boundary rules discovered on ArchitectureRules by reflection")
                .containsExactlyInAnyOrderElementsOf(SPEC_RULE_NAMES);
    }

    @Test
    void suite_declaresOneRuleFieldPerSpecRule_soNoTwoFieldsCollapseIntoOneName() {
        assertThat(RuleSuite.byFieldName())
                .as("public static final ArchRule fields on ArchitectureRules")
                .hasSize(SPEC_RULE_NAMES.size());
    }

    @Test
    void everyRule_carriesItsSpecNameAndAStatedReason() {
        for (Map.Entry<String, ArchRule> entry : RuleSuite.byFieldName().entrySet()) {
            String description = entry.getValue().getDescription();

            assertThat(description)
                    .as("%s must open with its spec name followed by the name separator", entry.getKey())
                    .contains(ArchitectureRules.NAME_SEPARATOR);

            assertThat(description)
                    .as("%s must state why the boundary exists, not only what it forbids", entry.getKey())
                    .contains(", because ");
        }
    }
}
