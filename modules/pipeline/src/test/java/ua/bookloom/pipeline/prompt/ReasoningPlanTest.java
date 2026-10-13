package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

/**
 * Which named calls ask the model to think (15i.X2): none, as today, until {@code BOOKLOOM_REASONING} (else the
 * {@code bookloom.reasoning} property) names them as {@code NAME=on} entries, so the eval can measure each call alone.
 */
class ReasoningPlanTest {

    private final ListAppender<ILoggingEvent> warnings = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void listenToThePlan() {
        logger = (Logger) LoggerFactory.getLogger(ReasoningPlan.class);
        warnings.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        warnings.start();
        logger.addAppender(warnings);
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(warnings);
        System.clearProperty(ReasoningPlan.PROPERTY_NAME);
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void resolve_nothingSet_hasThinkingOffForEveryCall(final PromptName name) {
        assertThat(ReasoningPlan.resolve(Map.of(), null).isOn(name)).isFalse();
    }

    @Test
    void resolve_nothingSet_describesItselfAsOff() {
        assertThat(ReasoningPlan.resolve(Map.of(), null).token()).isEqualTo("off");
    }

    // The switch turns on the calls it names and no other; the environment wins over the property as a whole.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            nullValues = "null",
            value = {
                "DRAFT_BATCH_JSON=on|null|DRAFT_BATCH_JSON|true",
                "DRAFT_BATCH_JSON=on|null|DRAFT|false",
                "DRAFT_BATCH_JSON=on|null|REVIEWER|false",
                " draft_batch_json = ON |null|DRAFT_BATCH_JSON|true",
                "draft-batch-json=on|null|DRAFT_BATCH_JSON|true",
                "null|TERM_READING=on|TERM_READING|true",
                "null|TERM_READING=on|TERM_TIEBREAK|false",
                "DRAFT_BATCH_JSON=on|TERM_READING=on|TERM_READING|false",
                "DRAFT_BATCH_JSON=on|TERM_READING=on|DRAFT_BATCH_JSON|true",
                "DRAFT_BATCH_JSON=on,TERM_READING=on,BRIEF_SYNTHESIS=on|null|BRIEF_SYNTHESIS|true",
                "DRAFT_BATCH_JSON=on,TERM_READING=on,BRIEF_SYNTHESIS=on|null|SUMMARY|false",
                "REVIEWER=on,REVIEWER=off|null|REVIEWER|false",
                "REVIEWER=off|null|REVIEWER|false"
            })
    void resolve_envThenProperty_turnsOnTheNamedCalls(
            final String env, final String property, final PromptName name, final boolean expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(ReasoningPlan.ENV_NAME, env);

        assertThat(ReasoningPlan.resolve(environment, property).isOn(name)).isEqualTo(expected);
    }

    // The description lists the calls that are on in declaration order, so two reports of the same plan read alike.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "DRAFT_BATCH_JSON=on|DRAFT_BATCH_JSON=on",
                "TERM_READING=on,DRAFT_BATCH_JSON=on|DRAFT_BATCH_JSON=on,TERM_READING=on",
                "REVIEWER=off|off",
                "DRAFT_BATCH_JSON=on,DRAFT_BATCH_JSON=off|off"
            })
    void token_plan_listsTheCallsThatAreOn(final String env, final String expected) {
        assertThat(ReasoningPlan.resolve(Map.of(ReasoningPlan.ENV_NAME, env), null)
                        .token())
                .isEqualTo(expected);
    }

    // IF an entry names no known call, THEN it is ignored with one WARN and the others still apply.
    @Test
    void resolve_unknownCall_isIgnoredWithAWarnAndTheRestApplies() {
        final ReasoningPlan plan =
                ReasoningPlan.resolve(Map.of(ReasoningPlan.ENV_NAME, "NO_SUCH_CALL=on,REVIEWER=on"), null);

        assertThat(plan.isOn(PromptName.REVIEWER)).isTrue();
        assertThat(plan.token()).isEqualTo("REVIEWER=on");
        assertThat(warnings.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("NO_SUCH_CALL"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"REVIEWER=maybe|maybe", "REVIEWER|REVIEWER", "=on|=on"})
    void resolve_malformedEntry_isIgnoredWithAWarn(final String env, final String shown) {
        final ReasoningPlan plan = ReasoningPlan.resolve(Map.of(ReasoningPlan.ENV_NAME, env), null);

        assertThat(plan.token()).isEqualTo("off");
        assertThat(warnings.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(shown));
    }

    @Test
    void resolve_validPlan_logsNoWarning() {
        ReasoningPlan.resolve(Map.of(ReasoningPlan.ENV_NAME, "REVIEWER=on"), null);

        assertThat(warnings.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .isEmpty();
    }

    @Test
    void current_propertySet_turnsOnTheNamedCall() {
        System.setProperty(ReasoningPlan.PROPERTY_NAME, "SUMMARY=on");

        assertThat(ReasoningPlan.current().isOn(PromptName.SUMMARY)).isTrue();
    }

    @Test
    void current_nothingSet_isOffForADraft() {
        assertThat(ReasoningPlan.current().isOn(PromptName.DRAFT)).isFalse();
    }
}
