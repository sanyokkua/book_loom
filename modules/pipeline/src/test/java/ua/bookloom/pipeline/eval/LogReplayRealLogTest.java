package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Reads the log named by {@code BOOKLOOM_EVAL_REPLAY_LOG} (the first {@code BOOKLOOM_EVAL_REPLAY_MAX_BYTES}, 2 MB when
 * unset) without sending anything, to check the parser against a real trace. It logs counts only. Skipped when no log
 * is named.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_REPLAY_LOG", matches = ".+")
class LogReplayRealLogTest {

    private static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;

    @Test
    void load_realTraceLog_findsCallsAndASelection() {
        final ReplayConfig config = ReplayConfig.fromEnv(withDefaultLimit()).orElseThrow();

        final LogReplayCorpus corpus = LogReplayCorpus.load(config.log(), config.maxBytes());
        final var selection = ReplaySelection.fast(corpus, config.target());

        log.info(
                "Real log: calls={} decisions={} fast selection={}",
                corpus.calls().size(),
                corpus.decisions().size(),
                selection.size());
        assertThat(corpus.calls()).isNotEmpty();
        assertThat(selection).isNotEmpty();
        assertThat(corpus.calls())
                .allSatisfy(call -> assertThat(call.requestBody()).startsWith("{"));
    }

    private static Map<String, String> withDefaultLimit() {
        final Map<String, String> env = new java.util.HashMap<>(System.getenv());
        env.putIfAbsent(ReplayConfig.MAX_BYTES_ENV, String.valueOf(DEFAULT_MAX_BYTES));
        return env;
    }
}
