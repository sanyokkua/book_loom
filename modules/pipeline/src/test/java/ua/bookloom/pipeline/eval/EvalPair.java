package ua.bookloom.pipeline.eval;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The language pair a mini eval run reads ({@code BOOKLOOM_EVAL_PAIR}): it picks the dataset folder and, with the dial,
 * goes into the report and history names so runs of different pairs or dials never overwrite each other.
 */
@Slf4j
@RequiredArgsConstructor
enum EvalPair {
    /** English to Ukrainian, the {@code mini-en} dataset. */
    EN_UK("en-uk", MiniBook.MINI_EN),
    /** Russian to Ukrainian, the {@code mini-ru} dataset. */
    RU_UK("ru-uk", MiniBook.MINI_RU);

    static final String ENV = "BOOKLOOM_EVAL_PAIR";

    private final String tag;
    private final String dataset;

    /** The dataset folder id under {@code eval/datasets}. */
    String dataset() {
        return dataset;
    }

    /**
     * The pair the environment asks for.
     *
     * @param env the non-null environment
     * @return the named pair, or {@link #EN_UK} when it is unset or blank
     * @throws IllegalArgumentException when the value names no pair
     */
    static EvalPair fromEnvironment(final Map<String, String> env) {
        final String asked =
                Objects.requireNonNull(env, "env").getOrDefault(ENV, "").strip().toLowerCase(Locale.ROOT);
        if (asked.isEmpty()) {
            return EN_UK;
        }
        for (final EvalPair pair : values()) {
            if (pair.tag.equals(asked)) {
                log.debug("Eval pair {} dataset={}", pair.tag, pair.dataset);
                return pair;
            }
        }
        throw new IllegalArgumentException(ENV + " takes en-uk or ru-uk, not " + asked);
    }

    /** The run's name part: {@code <pair>-<dial>-<policy>}, the dial in lower case. */
    String label(final String dial, final String policy) {
        return tag + "-" + dial.toLowerCase(Locale.ROOT) + "-" + policy;
    }
}
