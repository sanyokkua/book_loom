package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ChatModel;

/**
 * Runs a dataset K times, repeat k with the seed {@code 1000 + k}, so the spread of the K run-level aggregates shows
 * how far a score moves by chance and any one repeat can be sent again exactly. K comes from the matrix script's
 * {@code --repeats} as {@value #ENV}; unset is one repeat.
 *
 * @param k the number of repeats, at least 1
 */
@Slf4j
record EvalRepeats(int k) {

    static final String ENV = "BOOKLOOM_EVAL_REPEATS";
    static final int BASE_SEED = 1000;

    /** Rejects fewer than one repeat. */
    EvalRepeats {
        if (k < 1) {
            throw new IllegalArgumentException("repeats must be at least 1, not " + k);
        }
    }

    /**
     * The repeats the environment asks for.
     *
     * @param env the non-null environment
     * @return {@value #ENV} repeats, or one when it is unset or blank
     */
    static EvalRepeats fromEnvironment(final Map<String, String> env) {
        final String asked =
                Objects.requireNonNull(env, "env").getOrDefault(ENV, "").strip();
        final EvalRepeats repeats = new EvalRepeats(asked.isEmpty() ? 1 : Integer.parseInt(asked));
        log.debug("Eval repeats asked={} k={}", asked, repeats.k());
        return repeats;
    }

    /** The seed of each repeat, in order: {@code 1000 … 1000 + K - 1}. */
    List<Integer> seeds() {
        return IntStream.range(0, k).map(repeat -> BASE_SEED + repeat).boxed().toList();
    }

    /**
     * Runs the dataset once per repeat, each run on the model seeded for that repeat.
     *
     * @param model the non-null model under test
     * @param once the non-null run of the whole dataset on the seeded model
     * @param aggregate the non-null run-level score of one run's result
     * @param <T> what one run leaves
     * @return each repeat's seed, result and aggregate, in repeat order
     */
    <T> Runs<T> run(final ChatModel model, final Function<ChatModel, T> once, final ToDoubleFunction<T> aggregate) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(once, "once");
        Objects.requireNonNull(aggregate, "aggregate");
        final List<T> results = new ArrayList<>();
        final List<Double> aggregates = new ArrayList<>();
        for (int repeat = 0; repeat < k; repeat++) {
            final T result = once.apply(SeededModel.of(model, BASE_SEED, repeat));
            results.add(result);
            aggregates.add(aggregate.applyAsDouble(result));
            log.info("Eval repeat {}/{} seed={} aggregate={}", repeat + 1, k, BASE_SEED + repeat, aggregates.getLast());
        }
        return new Runs<>(seeds(), results, aggregates);
    }

    /**
     * What K repeats left.
     *
     * @param seeds each repeat's seed
     * @param results each repeat's result
     * @param aggregates each repeat's run-level score
     * @param <T> what one run leaves
     */
    record Runs<T>(List<Integer> seeds, List<T> results, List<Double> aggregates) {

        /** Copies the lists. */
        Runs {
            seeds = List.copyOf(seeds);
            results = List.copyOf(results);
            aggregates = List.copyOf(aggregates);
        }
    }
}
