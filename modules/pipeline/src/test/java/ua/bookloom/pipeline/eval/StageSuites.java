package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatModel;

/** Maps a {@code BOOKLOOM_EVAL_SUITE} name of the stage suites to the runner and cases that measure it. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StageSuites {

    /** The suite names, as the environment variable and the report carry them. */
    static final List<String> NAMES = List.of("prescan", "terms", "setup", "consistency", "retry", "replay");

    static StageReport run(final String suite, final String modelId, final ChatModel model, final Path workDir) {
        final List<StageRow> rows =
                switch (suite) {
                    case "prescan" -> new PreScanEvalRunner(model).run(StageCases.prescan());
                    case "terms" -> new TermsEvalRunner(model).run(StageCases.terms());
                    case "setup" -> new SetupEvalRunner(model, workDir).run(StageCases.setup());
                    case "consistency" -> new RetryEvalRunner(model, workDir).consistency(StageCases.retry());
                    case "retry" -> new RetryEvalRunner(model, workDir).retry(StageCases.retry());
                    case "replay" -> replay(model);
                    default -> throw new IllegalArgumentException("unknown stage suite: " + suite);
                };
        return new StageReport(suite, modelId, rows);
    }

    // The replay reads a log the person names; the suite is skipped before this when none is named.
    private static List<StageRow> replay(final ChatModel model) {
        final ReplayConfig config = ReplayConfig.fromEnv(System.getenv()).orElseThrow();
        return new ReplayRunner(model).run(LogReplayCorpus.load(config.log(), config.maxBytes()), config);
    }
}
