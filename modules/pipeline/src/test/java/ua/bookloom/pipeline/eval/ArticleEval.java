package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;

/**
 * The article eval (redesign task 1.11): the English {@code article-gravity} Markdown through today's batched job into
 * one target language, then the export with no consistency pass, read for the flagged rate and for the protected spans
 * (URLs, DOIs, code identifiers) the exported Markdown still holds verbatim.
 */
@Slf4j
final class ArticleEval {

    private final QualityDial dial;
    private final @Nullable Integer window;
    private final String target;

    /**
     * Creates the eval.
     *
     * @param dial the quality dial the run is made on
     * @param window the context window the provider is taken to report, or null for the run's default
     * @param target the target language, one of {@link ArticleGravity#TARGETS}
     */
    ArticleEval(final QualityDial dial, @Nullable final Integer window, final String target) {
        this.dial = Objects.requireNonNull(dial, "dial");
        this.window = window;
        this.target = Objects.requireNonNull(target, "target");
        if (!ArticleGravity.TARGETS.contains(target)) {
            throw new IllegalArgumentException(
                    "article target must be one of " + ArticleGravity.TARGETS + ": " + target);
        }
    }

    /**
     * Runs the article unattended and exports it.
     *
     * @param model the model the job calls
     * @param workDir an empty directory the source and the export are written into
     * @return what the run decided and what the export kept
     */
    ArticleRun run(final ChatModel model, final Path workDir) {
        final Path book = copySource(workDir);
        final BookBrief brief =
                SequenceJobs.brief(ArticleGravity.SOURCE, target, dial, SequenceNarratorMode.UNSET.narrator());
        final Prepared prepared = SequenceJobs.importBook(book, brief);
        log.info(
                "Article eval start project={} target={} dial={} window={}",
                prepared.projectId(),
                target,
                dial,
                window);
        final AtomicInteger calls = new AtomicInteger();
        final Instant started = Instant.now();
        final JobReport report = translate(prepared, model, calls);
        final String exported = export(prepared, workDir.resolve("gravity." + target + ".md"));
        final Duration elapsed = Duration.between(started, Instant.now());
        final ArticleRun run = result(prepared, report, elapsed, calls.get(), exported);
        log.info(
                "Article eval end state={} segments={} flagged={} keptShare={} elapsed={}",
                run.end(),
                run.segments(),
                run.flagged(),
                run.keptShare(),
                elapsed);
        return run;
    }

    private JobReport translate(final Prepared prepared, final ChatModel model, final AtomicInteger calls) {
        return Objects.requireNonNull(
                SequenceJobs.run(prepared, model, ReviewMode.UNATTENDED, window, event -> {
                            if (event instanceof ModelCallFinished) {
                                calls.incrementAndGet();
                            }
                        })
                        .data(),
                "job report");
    }

    private ArticleRun result(
            final Prepared prepared,
            final JobReport report,
            final Duration elapsed,
            final int calls,
            final String exported) {
        final Map<String, @Nullable String> outputs = outputs(prepared);
        final List<SegmentRecord> records = records(prepared);
        final int flagged = (int) records.stream()
                .filter(r -> r.status() == SegmentStatus.FLAGGED)
                .count();
        final String source = ArticleGravity.markdown(ArticleGravity.SOURCE);
        return new ArticleRun(
                target,
                report.end(),
                elapsed,
                calls,
                records.size(),
                flagged,
                outputs,
                ArticleGravity.keptShare(source, exported),
                ArticleGravity.lostSpans(source, exported));
    }

    // Book order: the parsed document's segments, each with the target its record stored.
    private static Map<String, @Nullable String> outputs(final Prepared prepared) {
        final Map<String, SegmentRecord> stored =
                records(prepared).stream().collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity()));
        final Map<String, @Nullable String> outputs = new LinkedHashMap<>();
        prepared.document().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .map(Segment::id)
                .filter(stored::containsKey)
                .forEach(id ->
                        outputs.put(id, Objects.requireNonNull(stored.get(id)).maskedMachineTarget()));
        return outputs;
    }

    private static List<SegmentRecord> records(final Prepared prepared) {
        return Objects.requireNonNull(
                prepared.stores().segments().all(prepared.projectId()).data(), "segment records");
    }

    private static String export(final Prepared prepared, final Path destination) {
        log.debug("Article eval export to {}", destination);
        final ExportRequest request = new ExportRequest(prepared.projectId(), destination, true, Set.of());
        final Result<ExportJob> job = SequenceJobs.exportService(prepared).newExport(request, null);
        final Result<ExportReport> done = Objects.requireNonNull(job.data(), () -> "export job: " + job.error())
                .run();
        final ExportReport report = Objects.requireNonNull(done.data(), () -> "export: " + done.error());
        try {
            return Files.readString(report.destination(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The source keeps its own image beside it, as the dataset folder does, so the import reads the same book.
    private static Path copySource(final Path workDir) {
        final Path folder = ArticleGravity.folder().resolve(ArticleGravity.SOURCE);
        try {
            Files.copy(folder.resolve(ArticleGravity.IMAGE), workDir.resolve(ArticleGravity.IMAGE));
            return Files.copy(
                    ArticleGravity.file(ArticleGravity.SOURCE, "md"), workDir.resolve(ArticleGravity.STEM + ".md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
