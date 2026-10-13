package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Names;
import ua.bookloom.pipeline.SequenceJobs.Prepared;

/**
 * Runs Names &amp; style over one book the way the app does, through the production {@code GlossaryService} and
 * {@code LexiconService} over the imported project's stores: the name scan, the review and the translate step, then the
 * recurring-term scan, review and suggest step; the final glossary and lexicon are scored against the book's gold.
 */
@Slf4j
final class NamesRunner {

    private final StageProbe probe;
    private final QualityDial dial;
    private List<LexiconEntry> lexicon = List.of();

    NamesRunner(final ChatModel model) {
        this(model, QualityDial.FAST);
    }

    NamesRunner(final ChatModel model, final QualityDial dial) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
        this.dial = Objects.requireNonNull(dial, "dial");
    }

    /** The recurring terms the last run ended with, renderings included. */
    List<LexiconEntry> lexicon() {
        return lexicon;
    }

    /**
     * Runs a committed book and scores it.
     *
     * @param book the non-null book with its gold and the dir to write it into
     * @param dir an empty directory the book's file is written into
     * @return the row, with an error code when a step failed
     */
    NamesRow run(final NamesBook book, final Path dir) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(dir, "dir");
        return run(book.id(), book.write(dir), book.gold(), prepared -> book.paragraphs());
    }

    /**
     * Runs a book file of the owner's gold set and scores it; mentions are counted in the book's story text.
     *
     * @param id the row's id
     * @param file the book file
     * @param gold the gold of the book
     * @return the row, with an error code when a step failed
     */
    NamesRow run(final String id, final Path file, final JudgementGold gold) {
        Objects.requireNonNull(file, "file");
        return run(id, file, gold, NamesRunner::storyParagraphs);
    }

    private NamesRow run(
            final String bookId,
            final Path file,
            final JudgementGold gold,
            final Function<Prepared, List<String>> paragraphs) {
        Objects.requireNonNull(gold, "gold");
        log.info("Names eval {} starts", bookId);
        final long started = System.nanoTime();
        final int mark = probe.mark();
        final Prepared prepared = SequenceJobs.importBook(
                file, SequenceJobs.brief(gold.sourceLanguage(), gold.targetLanguage(), dial, Narrator.unspecified()));
        final Names names = SequenceJobs.names(prepared);
        final String id = prepared.projectId();
        final String failed = firstFailure(bookId, names, id);
        if (failed != null) {
            return new NamesRow(bookId, null, failed);
        }
        lexicon = names.lexicon().entries(id).data();
        final NamesScore score = NamesScore.of(
                gold,
                paragraphs.apply(prepared),
                names.glossary().entries(id).data(),
                lexicon,
                probe.since(mark).calls(),
                Duration.ofNanos(System.nanoTime() - started));
        log.info("Names eval {} done: {}", bookId, score.json());
        return new NamesRow(bookId, score, null);
    }

    private @Nullable String firstFailure(final String bookId, final Names names, final String id) {
        for (final var step : steps(names, id).entrySet()) {
            final Result<?> result = step.getValue().get();
            if (result.isErr()) {
                log.warn(
                        "Names eval {} stopped at {}: {}",
                        bookId,
                        step.getKey(),
                        result.error().code());
                return result.error().code().name();
            }
            log.debug("Names eval {} finished {}", bookId, step.getKey());
        }
        return null;
    }

    private static List<String> storyParagraphs(final Prepared prepared) {
        return prepared.story().stream().map(Segment::sourceInner).toList();
    }

    private Map<String, Supplier<Result<?>>> steps(final Names names, final String id) {
        final Map<String, Supplier<Result<?>>> steps = new LinkedHashMap<>();
        steps.put("glossary scan", () -> names.glossary().scan(id));
        steps.put("prescan", () -> names.glossary().prescan(id, probe, event -> {}));
        steps.put("glossary review", () -> names.glossary().review(id, probe, event -> {}));
        steps.put("translate names", () -> names.glossary().translate(id, probe, event -> {}));
        steps.put("lexicon scan", () -> names.lexicon().scan(id));
        steps.put("lexicon model scan", () -> names.lexicon().scanWithModel(id, probe, event -> {}));
        steps.put("lexicon review", () -> names.lexicon().review(id, probe, event -> {}));
        steps.put("lexicon suggest", () -> names.lexicon().suggest(id, probe, event -> {}));
        return steps;
    }
}
