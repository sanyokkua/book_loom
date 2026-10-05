package ua.bookloom.app.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.RenderingConsistency;

/**
 * The command's JSON summary of one run, for a script or a person comparing runs: what was translated with what, how
 * long each phase took, how the run ended and why, every flagged segment with its reason, each outage waited through,
 * the names review and what the export wrote. It holds counts, ids, codes and timings only — never book text.
 */
@Slf4j
final class RunReport {

    private static final double MILLIS_PER_SECOND = 1000.0;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectNode root = mapper.createObjectNode();
    private final ObjectNode timings = root.putObject("timings");

    /**
     * Starts the report of a command.
     *
     * @param arguments the parsed command; never null
     * @param destination the book the command writes; never null
     * @param startedAt when the command started; never null
     */
    RunReport(TranslateArguments arguments, Path destination, Instant startedAt) {
        root.put("book", arguments.source().toString());
        root.put("format", arguments.format().name());
        root.put("destination", destination.toString());
        root.put("targetLanguage", arguments.targetLanguage());
        root.put("startedAt", startedAt.toString());
    }

    void selection(ModelSelection selection) {
        root.put("provider", selection.providerId());
        root.put("model", selection.modelId());
    }

    void brief(BookBrief brief) {
        root.put("sourceLanguage", brief.sourceLanguage());
        root.put("quality", brief.dial().name());
        root.put("names", brief.names().name());
    }

    void phase(String name, Duration took) {
        timings.put(name + "Seconds", seconds(took));
    }

    void names(NameReview.Counts counts) {
        final ObjectNode names = root.putObject("namesReview");
        names.put("found", counts.scanned());
        names.put("removed", counts.removed());
        names.put("typed", counts.updated());
        names.put("suggested", counts.suggested());
        names.put("accepted", counts.accepted());
        names.put("failed", counts.error());
    }

    void run(Result<JobReport> result, UnattendedRun watch) {
        final ObjectNode run = root.putObject("run");
        run.put("stoppedBecause", watch.stopReason());
        timings.put("modelCalls", watch.modelCalls());
        timings.put("modelSeconds", seconds(watch.modelTime()));
        modelCallsByKind(watch);
        recovery(watch);
        flagged(watch);
        progress(run, watch.lastProgress());
        final @Nullable JobReport report = result.data();
        if (report == null) {
            error(run, Objects.requireNonNull(result.error(), "error"));
            return;
        }
        run.put("end", report.end().name());
        run.put("segments", report.segments());
        run.put("accepted", report.accepted());
        run.put("flagged", report.flagged());
        final @Nullable AppError error = report.error();
        if (error != null) {
            error(run, error);
        }
        final ArrayNode reasons = run.putArray("flaggedSegments");
        for (final FlaggedSegment segment : report.flaggedSegments()) {
            reasons.addObject().put("segmentId", segment.segmentId()).put("reason", String.valueOf(segment.reason()));
        }
    }

    /**
     * Records how consistently the run kept its recurring terms: how many terms it tracked, how many it saw used, how
     * many renderings it saw per term used (1.0 is one rendering each) and how many terms were written two ways.
     */
    void lexicon(RenderingConsistency consistency) {
        final ObjectNode lexicon = root.putObject("lexicon");
        lexicon.put("terms", consistency.terms());
        lexicon.put("used", consistency.used());
        lexicon.put("renderings", consistency.renderings());
        lexicon.put("distinctRenderingsPerTerm", consistency.distinctPerTerm());
        lexicon.put("conflicted", consistency.conflicted());
    }

    void export(ExportReport report, boolean partial) {
        final ObjectNode export = root.putObject("export");
        export.put("partial", partial);
        export.put("written", report.written());
        export.put("pending", report.pending());
        export.put("flaggedWritten", report.flaggedWritten());
        export.put("autoAccepted", report.autoAccepted());
        export.put("keptVerbatim", report.keptVerbatim());
        export.put("verifiedSegments", report.verifiedSegments());
        final ArrayNode fallbacks = export.putArray("sourceFallbacks");
        for (final SourceFallback fallback : report.sourceFallbacks()) {
            fallbacks.addObject().put("segmentId", fallback.segmentId()).put("locator", fallback.locator());
        }
    }

    /**
     * Ends the report and writes it.
     *
     * @param file where it goes; never null
     * @param endedAt when the command ended; never null
     * @param exitCode the command's exit code
     * @return the written file, or {@code ErrorCode.internal} when it could not be written
     */
    Result<Path> write(Path file, Instant endedAt, int exitCode) {
        root.put("endedAt", endedAt.toString());
        root.put("exitCode", exitCode);
        try {
            final @Nullable Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, mapper.writeValueAsString(root) + "\n", StandardCharsets.UTF_8);
            log.info("translate command report written file={}", file);
            return Result.ok(file);
        } catch (IOException failure) {
            log.warn("translate command report not written file={}", file, failure);
            return Result.err(AppError.of(
                    ErrorCode.internal, "The run report was not written", "The report file could not be written."));
        }
    }

    private void modelCallsByKind(UnattendedRun watch) {
        final ObjectNode kinds = root.putObject("modelCallsByKind");
        watch.kindTotals().totals().forEach((kind, totals) -> {
            final ObjectNode node = kinds.putObject(kind.name());
            node.put("attempts", totals.attempts());
            node.put("failed", totals.failed());
            node.put("promptTokens", totals.promptTokens());
            node.put("completionTokens", totals.completionTokens());
            node.put("cachedPromptTokens", totals.cachedPromptTokens());
            node.put("elapsedSeconds", seconds(totals.elapsed()));
            node.put("promptEvalSeconds", seconds(totals.promptEval()));
            node.put("generationSeconds", seconds(totals.generation()));
        });
    }

    private void recovery(UnattendedRun watch) {
        final ObjectNode recovery = root.putObject("recovery");
        recovery.put("waits", watch.waits());
        final ArrayNode outages = recovery.putArray("outages");
        for (final UnattendedRun.Outage outage : watch.outages()) {
            outages.addObject()
                    .put("downSince", outage.downSince().toString())
                    .put("code", outage.code().name())
                    .put("wakes", outage.wakes())
                    .put("ended", outage.ended());
        }
    }

    private void flagged(UnattendedRun watch) {
        final ArrayNode flagged = root.putArray("flagged");
        for (final UnattendedRun.FlaggedDetail detail : watch.flagged()) {
            final ObjectNode node = flagged.addObject();
            node.put("segmentId", detail.segmentId());
            final @Nullable ErrorCode reason = detail.reason();
            node.put("reason", reason == null ? null : reason.name());
            final ArrayNode kinds = node.putArray("findings");
            detail.findingKinds().forEach(kinds::add);
        }
    }

    private static void progress(ObjectNode run, @Nullable JobProgress progress) {
        if (progress != null) {
            run.put("autoAccepted", progress.autoAccepted());
            run.put("repairedAccepted", progress.repairedAccepted());
            run.put("keptVerbatim", progress.keptVerbatim());
            run.put("pending", progress.pending());
        }
    }

    private static void error(ObjectNode run, AppError error) {
        run.putObject("error").put("code", error.code().name()).put("title", error.title());
    }

    private static double seconds(Duration took) {
        return took.toMillis() / MILLIS_PER_SECOND;
    }
}
