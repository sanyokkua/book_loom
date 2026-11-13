package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.llm.ReasoningLevel;

/**
 * Arm 1 of design D24, the judge calibration: the local judge grades the {@code cal-1} items of the splits
 * {@code BOOKLOOM_EVAL_CAL_SPLITS} names (comma-separated, default {@code dev}; the hidden {@code test} split is run
 * once, after the rubric is frozen). Local-only: {@code BOOKLOOM_EVAL_SUITE=cal BOOKLOOM_EVAL_URL=…
 * ./gradlew :pipeline:promptEval --tests '*JudgeCalibrationEvalTest'}. {@code BOOKLOOM_EVAL_MODEL} is the judge
 * (default {@code gemma4:26b-mlx}), sent through the production chat model and its inference gate, so it runs alone;
 * thinking is off unless {@code BOOKLOOM_EVAL_JUDGE_THINK=on}. {@code BOOKLOOM_EVAL_RUBRIC_FILE} names a rubric file
 * being tuned in place of the frozen bundled {@code bl-judge-1}. The report lands in the eval history, where
 * {@code scripts/eval-score.py calibrate --judge latest:cal} reads it.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "cal")
class JudgeCalibrationEvalTest {

    static final String DEFAULT_JUDGE = "gemma4:26b-mlx";
    private static final String DEFAULT_SPLITS = "dev";

    @Test
    void calibration_realJudge_gradesEveryItemOfTheAskedSplits() throws IOException {
        final Map<String, String> env = System.getenv();
        final String model = env.getOrDefault("BOOKLOOM_EVAL_MODEL", DEFAULT_JUDGE);
        final Set<String> splits = splits(env.getOrDefault("BOOKLOOM_EVAL_CAL_SPLITS", DEFAULT_SPLITS));
        final ReasoningLevel thinking = "on".equalsIgnoreCase(env.getOrDefault("BOOKLOOM_EVAL_JUDGE_THINK", "off"))
                ? ReasoningLevel.ON
                : ReasoningLevel.OFF;
        final JudgeRubric rubric = rubric(env.get("BOOKLOOM_EVAL_RUBRIC_FILE"));
        final JudgeRunner judge = judge(model, env.get("BOOKLOOM_EVAL_QUANT"), thinking, rubric);
        final List<CalItems.Item> items = CalItems.loadBundled(CalItems.CAL_1).inSplits(splits);
        log.info(
                "Judge calibration judge={} splits={} items={} thinking={} rubric={}",
                model,
                splits,
                items.size(),
                thinking,
                rubric.hash());

        final JudgeCalibration.Report report = new JudgeCalibration(judge).run(items);

        final String label = label(splits, thinking);
        EvalOutput.write(
                EvalProvenance.capture(JudgeCalibration.SUITE, model, label).withJudge(judge.block("none")),
                model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + JudgeCalibration.SUITE + "-" + label,
                report.table(),
                report.json(CalItems.CAL_1, splits),
                Path.of("build", "reports", "promptEval"),
                EvalOutput.historyRoot(env, EvalProvenance.repoRoot()),
                List.of());
        log.info("Judge calibration report\n{}", report.table());
        assertThat(report.judged()).hasSameSizeAs(items);
    }

    // The production chat model: its inference gate keeps the judge single-flight.
    private static JudgeRunner judge(
            final String model, @Nullable final String quant, final ReasoningLevel thinking, final JudgeRubric rubric) {
        return new JudgeRunner(
                PromptEvalTest.chatModel(model),
                new JudgeRunner.Setup(model, quant, null, thinking),
                rubric,
                JudgeRunner.defaultCacheDir());
    }

    // The history folder name tells a dev run from a test run and a thinking run from one without.
    private static String label(final Set<String> splits, final ReasoningLevel thinking) {
        return String.join("+", splits.stream().sorted().toList()) + "-think-"
                + thinking.name().toLowerCase(Locale.ROOT);
    }

    private static Set<String> splits(final String asked) {
        return Arrays.stream(asked.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static JudgeRubric rubric(@Nullable final String file) {
        return file == null || file.isBlank() ? JudgeRubric.load() : JudgeRubric.load(Path.of(file.strip()));
    }
}
