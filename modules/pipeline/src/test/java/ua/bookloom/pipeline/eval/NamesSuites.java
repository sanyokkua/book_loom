package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The names suite (15i.E1): Names &amp; style end to end on the committed failure books
 * ({@code BOOKLOOM_EVAL_BOOK}, a {@link NamesBook} id or {@code all}, the default) and, when
 * {@code BOOKLOOM_CORPUS_DIR} is set, on every {@code *.gold.json} directly under it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NamesSuites {

    static final String BOOK_ENV = JudgementSuites.BOOK_ENV;
    static final String CORPUS_ENV = JudgementSuites.CORPUS_ENV;

    private static final String ALL = "all";

    /** Runs the committed books the environment names, then the owner's gold books when a corpus is set. */
    static NamesReport run(
            final String modelId, final ChatModel model, final Path workDir, final Map<String, String> env) {
        Objects.requireNonNull(modelId, "modelId");
        final QualityDial dial = dial(env);
        log.info("Names eval {} at dial {}", modelId, dial);
        final NamesRunner runner = new NamesRunner(model, dial);
        final List<NamesRow> rows = new ArrayList<>();
        for (final NamesBook book : books(env.get(BOOK_ENV))) {
            rows.add(runner.run(book, workDirFor(workDir, book.id())));
        }
        final String corpus = env.getOrDefault(CORPUS_ENV, "");
        if (!corpus.isBlank()) {
            for (final Path file : JudgementSuites.goldFiles(Path.of(corpus))) {
                rows.add(goldRow(file, model, dial));
            }
        }
        log.info("Names eval {} ran {} books", modelId, rows.size());
        return new NamesReport(modelId, rows);
    }

    private static Path workDirFor(final Path workDir, final String id) {
        try {
            return Files.createDirectories(workDir.resolve(id));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create " + workDir.resolve(id), e);
        }
    }

    /**
     * The dial the books are read at: {@code BOOKLOOM_EVAL_DIAL} when set, else Balanced, the dial the report's
     * provenance names (the suite ran at Fast before, whatever the provenance said).
     */
    static QualityDial dial(final Map<String, String> env) {
        final String dial = env.get(JudgementRunner.DIAL_ENV);
        return dial == null || dial.isBlank()
                ? QualityDial.BALANCED
                : QualityDial.valueOf(dial.strip().toUpperCase(Locale.ROOT));
    }

    private static NamesRow goldRow(final Path file, final ChatModel model, final QualityDial dial) {
        final JudgementGold gold = JudgementGold.read(file);
        final String book = Objects.requireNonNull(gold.book(), () -> "no \"book\" in " + file);
        final String id = file.getFileName().toString().replace(JudgementSuites.GOLD_SUFFIX, "");
        return new NamesRunner(model, dial).run(id, file.getParent().resolve(book), gold);
    }

    /** The committed books a {@code BOOKLOOM_EVAL_BOOK} value names; all of them when it is unset or {@code all}. */
    static List<NamesBook> books(@Nullable final String spec) {
        if (spec == null || spec.isBlank() || ALL.equals(spec.strip())) {
            return NamesBook.all();
        }
        final String id = spec.strip();
        if (!NamesBook.IDS.contains(id)) {
            throw new IllegalArgumentException(BOOK_ENV + " must be all or one of " + NamesBook.IDS + ", not " + id);
        }
        return List.of(NamesBook.byId(id));
    }
}
