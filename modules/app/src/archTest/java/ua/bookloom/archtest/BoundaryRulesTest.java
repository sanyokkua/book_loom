package ua.bookloom.archtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Runs every boundary rule against the production classes of all eight modules.
 *
 * <p>The first two tests exist because the third one, on its own, proves nothing. A rule set evaluated against an
 * empty class set is green; so is a rule set evaluated against only the classes it was never meant to police.
 * {@code productionImport_*} pins down what the third test is actually looking at before it looks.
 *
 * <p>Covers tasks 4.1–4.8 and 4.10 of the {@code bootstrap-gradle-and-quality-toolchain} change.
 */
class BoundaryRulesTest {

    private static final JavaClasses PRODUCTION = ProductionClasses.imported();

    // Covers task 4.10: a rule evaluated against a module the importer never found is a rule that cannot fail.
    @Test
    void productionImport_coversAllEightModules_soNoRuleIsEvaluatedAgainstAnAbsentModule() {
        for (String module : ModuleGraph.ALL_MODULES) {
            List<String> classes = PRODUCTION.stream()
                    .filter(javaClass -> ModuleGraph.moduleOf(javaClass)
                            .filter(module::equals)
                            .isPresent())
                    .map(JavaClass::getName)
                    .toList();

            assertThat(classes)
                    .as("production classes imported from :%s — an empty module makes every rule vacuous", module)
                    .isNotEmpty();
        }
    }

    // Covers task 4.9: the fixtures are deliberate violations, so leaking them into the production import would
    // make the whole suite unable to be green — and would mask any real violation behind the noise.
    @Test
    void productionImport_excludesTheArchTestFixtures() {
        List<String> leaked = PRODUCTION.stream()
                .map(JavaClass::getName)
                .filter(name -> name.contains(".archfixture."))
                .toList();

        assertThat(leaked)
                .as("violation fixtures leaked into the production class import")
                .isEmpty();
    }

    // Covers tasks 4.2–4.8: the suite is discovered by reflection, so a rule added to ArchitectureRules is
    // evaluated here without anyone remembering to wire it up.
    @Test
    void productionCode_satisfiesEveryRuleInTheSuite() {
        Map<String, String> failures = new LinkedHashMap<>();

        for (Map.Entry<String, ArchRule> entry : RuleSuite.byFieldName().entrySet()) {
            EvaluationResult result = entry.getValue().evaluate(PRODUCTION);
            if (result.hasViolation()) {
                failures.put(
                        entry.getKey(),
                        String.join(
                                System.lineSeparator(),
                                result.getFailureReport().getDetails()));
            }
        }

        assertThat(failures).as("boundary rules violated by production code").isEmpty();
    }
}
