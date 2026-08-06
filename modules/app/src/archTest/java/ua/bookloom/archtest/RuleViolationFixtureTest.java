package ua.bookloom.archtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.archfixture.GuiceInjectedContract;
import ua.bookloom.api.archfixture.MutableCarrier;
import ua.bookloom.app.bootstrap.archfixture.Launcher;
import ua.bookloom.document.archfixture.FxDependentParser;
import ua.bookloom.document.archfixture.LombokAnnotatedService;
import ua.bookloom.llm.archfixture.SqlUsingClient;
import ua.bookloom.persistence.archfixture.FixtureProjectDao;
import ua.bookloom.pipeline.archfixture.HttpUsingCoordinator;
import ua.bookloom.ui.archfixture.ConcreteDaoConsumer;
import ua.bookloom.ui.archfixture.FxUsingView;
import ua.bookloom.util.archfixture.ReversedEdgeHelper;
import ua.bookloom.util.paths.archfixture.ClassInitLoggingResolver;
import ua.bookloom.util.paths.archfixture.StaticLoggerResolver;

/**
 * Feeds every boundary rule an input it must reject.
 *
 * <p>This is the reason task group 4 exists. All eight modules currently hold one placeholder type each, so every
 * rule in {@link ArchitectureRules} passes <em>vacuously</em> against production code: a rule whose package scope
 * is typo'd, whose predicate silently matches nothing, or which was quietly weakened during a refactor produces
 * output byte-identical to a healthy one. A green {@code archTest} is therefore not evidence of anything. The
 * only thing that distinguishes a working rule from a broken one is watching it go red on demand.
 *
 * <p>Each fixture is imported in isolation with {@code importClasses(...)} rather than by package, so a rule is
 * only ever confronted with the classes it is being tested on. Fixtures for other rules deliberately violate this
 * one too — {@code ConcreteDaoConsumer}, for instance, breaks both {@code ports-not-concretes} and
 * {@code dependency-direction} — and a shared import would let one rule's failure be mistaken for another's.
 *
 * <p>Covers task 4.9 of the {@code bootstrap-gradle-and-quality-toolchain} change.
 */
class RuleViolationFixtureTest {

    // ===== 1. fx-free-core ===============================================================================

    @Test
    void fxFreeCore_javafxInsideDocument_isRejected() {
        assertRejects(
                ArchitectureRules.FX_FREE_CORE, fixture(FxDependentParser.class), FxDependentParser.class.getName());
    }

    /** The mis-scoping that would pass every positive test: widening the rule past the six core modules. */
    @Test
    void fxFreeCore_javafxInsideUi_isAccepted() {
        assertAccepts(ArchitectureRules.FX_FREE_CORE, fixture(FxUsingView.class));
    }

    // ===== 2. dependency-direction =======================================================================

    @Test
    void dependencyDirection_utilDependingOnPipeline_isRejected() {
        assertRejects(ArchitectureRules.DEPENDENCY_DIRECTION, fixture(ReversedEdgeHelper.class), ":util -> :pipeline");
    }

    // ===== 3. ports-not-concretes ========================================================================

    @Test
    void portsNotConcretes_uiDependingOnAConcreteDaoInPersistence_isRejected() {
        assertRejects(
                ArchitectureRules.PORTS_NOT_CONCRETES,
                fixture(ConcreteDaoConsumer.class, FixtureProjectDao.class),
                FixtureProjectDao.class.getSimpleName());
    }

    // ===== 4. no-http-in-core-except-llm =================================================================

    @Test
    void noHttpInCoreExceptLlm_httpClientInsidePipeline_isRejected() {
        assertRejects(
                ArchitectureRules.NO_HTTP_IN_CORE_EXCEPT_LLM,
                fixture(HttpUsingCoordinator.class),
                HttpUsingCoordinator.class.getName());
    }

    // ===== 5. no-sql-in-core-except-persistence ==========================================================

    @Test
    void noSqlInCoreExceptPersistence_jdbcConnectionInsideLlm_isRejected() {
        assertRejects(
                ArchitectureRules.NO_SQL_IN_CORE_EXCEPT_PERSISTENCE,
                fixture(SqlUsingClient.class),
                SqlUsingClient.class.getName());
    }

    // ===== 6. api-is-framework-free ======================================================================

    @Test
    void apiIsFrameworkFree_guiceAnnotationInsideApi_isRejected() {
        assertRejects(
                ArchitectureRules.API_IS_FRAMEWORK_FREE,
                fixture(GuiceInjectedContract.class),
                GuiceInjectedContract.class.getName());
    }

    // ===== 7. records-first ==============================================================================

    @Test
    void recordsFirst_mutableClassInsideAnApiPackage_isRejected() {
        assertRejects(ArchitectureRules.RECORDS_FIRST, fixture(MutableCarrier.class), MutableCarrier.class.getName());
    }

    /**
     * The DD-05 / ADR-0014 hybrid: Lombok on a service class is the intended design, not a defect. A
     * {@code records-first} scoped to {@code ua.bookloom..} instead of the carrier packages would reject this
     * while still passing the positive test above.
     */
    @Test
    void recordsFirst_lombokServiceOutsideCarrierPackages_isAccepted() {
        assertAccepts(ArchitectureRules.RECORDS_FIRST, fixture(LombokAnnotatedService.class));
    }

    // ===== 8. bootstrap-no-static-logger =================================================================

    @Test
    void bootstrapNoStaticLogger_staticLoggerFieldInUtilPaths_isRejected() {
        assertRejects(
                ArchitectureRules.BOOTSTRAP_NO_STATIC_LOGGER, fixture(StaticLoggerResolver.class), "org.slf4j.Logger");
    }

    /** No static logger field at all — the SLF4J call is inside {@code <clinit>}, which pins Logback just as hard. */
    @Test
    void bootstrapNoStaticLogger_slf4jCallFromStaticInitializer_isRejected() {
        assertRejects(
                ArchitectureRules.BOOTSTRAP_NO_STATIC_LOGGER,
                fixture(ClassInitLoggingResolver.class),
                "static initializer");
    }

    /**
     * Proves the {@code :app} half of the scope predicate selects something.
     *
     * <p>The scope is {@code ua.bookloom.app.bootstrap..} — a package, not a set of class names. That distinction is
     * what this fixture now guards: narrowing the rule back to name matching would leave the Logback bootstrap class
     * unprotected, and it is the one class in the project where a static logger silently misdirects every log file
     * the application writes.
     */
    @Test
    void bootstrapNoStaticLogger_staticLoggerFieldInAppLauncher_isRejected() {
        assertRejects(ArchitectureRules.BOOTSTRAP_NO_STATIC_LOGGER, fixture(Launcher.class), Launcher.class.getName());
    }

    // ===== helpers =======================================================================================

    private static JavaClasses fixture(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    private static void assertRejects(ArchRule rule, JavaClasses fixtureClasses, String expectedDetail) {
        EvaluationResult result = rule.evaluate(fixtureClasses);
        String ruleName = RuleSuite.nameOf(rule);

        assertThat(result.hasViolation())
                .as(
                        "%s must reject its violation fixture; a rule that cannot go red is indistinguishable "
                                + "from one that is switched off",
                        ruleName)
                .isTrue();

        assertThat(result.getFailureReport().getDetails())
                .as("%s reported a violation, but not the one the fixture seeds", ruleName)
                .anySatisfy(detail -> assertThat(detail).contains(expectedDetail));
    }

    private static void assertAccepts(ArchRule rule, JavaClasses fixtureClasses) {
        EvaluationResult result = rule.evaluate(fixtureClasses);

        assertThat(result.getFailureReport().getDetails())
                .as("%s is scoped too broadly and rejects a legitimate construct", RuleSuite.nameOf(rule))
                .isEmpty();
    }
}
