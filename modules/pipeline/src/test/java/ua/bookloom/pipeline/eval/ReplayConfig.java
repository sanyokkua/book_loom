package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the replay suite is asked for, read from the environment: {@code BOOKLOOM_EVAL_REPLAY_LOG} (a trace log, plain
 * or {@code .gz}; unset means no replay), {@code BOOKLOOM_EVAL_REPLAY_MODE} ({@code raw} or {@code fast}, default
 * {@code fast}), {@code BOOKLOOM_EVAL_REPLAY_MAX_BYTES} (read only the first bytes of the log; default the whole log),
 * {@code BOOKLOOM_EVAL_REPLAY_LIMIT} (send at most that many calls; default all) and
 * {@code BOOKLOOM_EVAL_REPLAY_TARGET} (the size of the fast selection; default 40).
 *
 * @param log the trace log
 * @param mode how the calls are replayed
 * @param maxBytes the most bytes of the log to read, or zero for all
 * @param limit the most calls to send, or zero for all
 * @param target the size the fast selection aims at; flagged segments are always kept, so it may be larger
 */
record ReplayConfig(Path log, Mode mode, long maxBytes, int limit, int target) {

    static final String LOG_ENV = "BOOKLOOM_EVAL_REPLAY_LOG";
    static final String MODE_ENV = "BOOKLOOM_EVAL_REPLAY_MODE";
    static final String MAX_BYTES_ENV = "BOOKLOOM_EVAL_REPLAY_MAX_BYTES";
    static final String LIMIT_ENV = "BOOKLOOM_EVAL_REPLAY_LIMIT";
    static final String TARGET_ENV = "BOOKLOOM_EVAL_REPLAY_TARGET";
    static final int DEFAULT_TARGET = 40;

    /** How the logged calls are sent again. */
    enum Mode {
        /** Each logged request body is sent again as it went out. */
        RAW("raw"),
        /** A stratified selection of the draft calls is rebuilt by the current prompts and sent. */
        FAST("fast");

        private final String word;

        Mode(final String word) {
            this.word = word;
        }

        String word() {
            return word;
        }

        static Mode of(final String value) {
            final String asked = value.strip().toLowerCase(Locale.ROOT);
            for (final Mode mode : values()) {
                if (mode.word.equals(asked)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("unknown replay mode (raw or fast): " + value);
        }
    }

    /** The configuration the environment asks for, or empty when no log is named. */
    static Optional<ReplayConfig> fromEnv(final Map<String, String> env) {
        final String log = env.getOrDefault(LOG_ENV, "").strip();
        if (log.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReplayConfig(
                Path.of(log),
                Mode.of(env.getOrDefault(MODE_ENV, Mode.FAST.word())),
                Long.parseLong(env.getOrDefault(MAX_BYTES_ENV, "0").strip()),
                Integer.parseInt(env.getOrDefault(LIMIT_ENV, "0").strip()),
                Integer.parseInt(env.getOrDefault(TARGET_ENV, String.valueOf(DEFAULT_TARGET))
                        .strip())));
    }
}
