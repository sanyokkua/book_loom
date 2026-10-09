package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.pipeline.eval.JudgementRunner.Judgement;

/**
 * The two suites of the model's judgement of a book (15h.E3). {@code stability} runs the brief suggestion and the name
 * review on a committed fixture book ({@code BOOKLOOM_EVAL_BOOK}, a {@link JudgementBook} id or {@code all}; default
 * {@value JudgementBook#DEFAULT_ID}) once per seed ({@code BOOKLOOM_EVAL_SEEDS}, default {@code 1,2,3}) and reports
 * how often the seeds agree. {@code gold} runs them on the owner's books ({@code *.gold.json} directly under
 * {@code BOOKLOOM_CORPUS_DIR}, each naming its book) and reports how often the model is right; one seed unless
 * {@code BOOKLOOM_EVAL_SEEDS} says more.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JudgementSuites {

    static final String BOOK_ENV = "BOOKLOOM_EVAL_BOOK";
    static final String SEEDS_ENV = "BOOKLOOM_EVAL_SEEDS";
    static final String CORPUS_ENV = "BOOKLOOM_CORPUS_DIR";
    static final String GOLD_SUFFIX = ".gold.json";
    static final String STABILITY = "stability";
    static final String GOLD = "gold";

    private static final String ALL = "all";
    private static final List<Integer> STABILITY_SEEDS = List.of(1, 2, 3);
    private static final List<Integer> GOLD_SEEDS = List.of(1);

    /** Runs the suite the name says, {@value #STABILITY} or {@value #GOLD}. */
    static JudgementReport run(
            final String suite,
            final String modelId,
            final ChatModel model,
            final Path workDir,
            final Map<String, String> env) {
        Objects.requireNonNull(suite, "suite");
        return switch (suite) {
            case STABILITY -> stability(modelId, model, workDir, env);
            case GOLD -> gold(modelId, model, Path.of(Objects.requireNonNull(env.get(CORPUS_ENV), CORPUS_ENV)), env);
            default -> throw new IllegalArgumentException("unknown judgement suite: " + suite);
        };
    }

    /** The stability suite over the fixture books the environment names. */
    static JudgementReport stability(
            final String modelId, final ChatModel model, final Path workDir, final Map<String, String> env) {
        final List<Integer> seeds = seeds(env.get(SEEDS_ENV), STABILITY_SEEDS);
        final List<JudgementReport.Book> books = new ArrayList<>();
        for (final JudgementBook book : books(env.get(BOOK_ENV))) {
            final Path file = book.write(workDir);
            books.add(
                    JudgementReport.score(book.id(), book.gold(), judged(model, seeds, book.id(), file, book.gold())));
        }
        return new JudgementReport(STABILITY, modelId, seeds, books);
    }

    /** The gold suite over the owner's gold files in {@code corpusDir}. */
    static JudgementReport gold(
            final String modelId, final ChatModel model, final Path corpusDir, final Map<String, String> env) {
        final List<Integer> seeds = seeds(env.get(SEEDS_ENV), GOLD_SEEDS);
        final List<JudgementReport.Book> books = new ArrayList<>();
        for (final Path file : goldFiles(corpusDir)) {
            final JudgementGold gold = JudgementGold.read(file);
            final String book = Objects.requireNonNull(gold.book(), () -> "no \"book\" in " + file);
            final Path path = file.getParent().resolve(book);
            final String id = file.getFileName().toString().replace(GOLD_SUFFIX, "");
            books.add(JudgementReport.score(id, gold, judged(model, seeds, id, path, gold)));
        }
        return new JudgementReport(GOLD, modelId, seeds, books);
    }

    private static List<Judgement> judged(
            final ChatModel model,
            final List<Integer> seeds,
            final String id,
            final Path file,
            final JudgementGold gold) {
        return seeds.stream()
                .map(seed -> {
                    log.info("Judgement eval book={} seed={}", id, seed);
                    return new JudgementRunner(new Seeded(model, seed)).judge(id, file, gold);
                })
                .toList();
    }

    /** The fixture books a {@code BOOKLOOM_EVAL_BOOK} value names. */
    static List<JudgementBook> books(@Nullable final String spec) {
        if (spec == null || spec.isBlank()) {
            return List.of(JudgementBook.byId(JudgementBook.DEFAULT_ID));
        }
        if (ALL.equals(spec.strip())) {
            return JudgementBook.all();
        }
        final String id = spec.strip();
        if (!JudgementBook.IDS.contains(id)) {
            throw new IllegalArgumentException(
                    BOOK_ENV + " must be all or one of " + JudgementBook.IDS + ", not " + id);
        }
        return List.of(JudgementBook.byId(id));
    }

    /** The seeds a {@code BOOKLOOM_EVAL_SEEDS} value lists, comma-separated; {@code fallback} when unset. */
    static List<Integer> seeds(@Nullable final String spec, final List<Integer> fallback) {
        if (spec == null || spec.isBlank()) {
            return fallback;
        }
        return Arrays.stream(spec.split(","))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .map(Integer::valueOf)
                .toList();
    }

    /** The owner's gold files directly under {@code dir}, by name. */
    static List<Path> goldFiles(final Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(GOLD_SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + dir, e);
        }
    }

    /**
     * Sends every call with one sampling seed, so the same book asked again under another seed shows how much of the
     * model's answer is chance. A request that already carries a seed (a retry after a timeout) keeps its own.
     */
    private record Seeded(ChatModel model, int seed) implements ChatModel {

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            return model.chat(seeded(request));
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
            return model.chat(seeded(request), attempts);
        }

        private ChatRequest seeded(final ChatRequest request) {
            return request.seed() == null ? request.withSeed(seed) : request;
        }
    }
}
