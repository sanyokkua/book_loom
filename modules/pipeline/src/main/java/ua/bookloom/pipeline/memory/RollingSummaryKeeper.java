package ua.bookloom.pipeline.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Keeps the rolling summary of one book: a short record of who is who that travels with every draft prompt, because
 * the model forgets earlier chapters. It is refreshed after every {@value #REFRESH_EVERY} accepted segments and at
 * the end of every unit from the glossary and the headings, at no cost; where the dial asks for it, the refresh at a
 * unit's end is one model call instead. Built by the run and used from its one thread, so it holds its counters
 * without locking.
 */
@Slf4j
public final class RollingSummaryKeeper {

    static final int REFRESH_EVERY = 20;

    private enum Trigger {
        COUNT,
        UNIT_END
    }

    private final SummaryRepository summaries;
    private final GlossaryRepository glossary;
    private final SummaryModelCall modelCall;
    private final boolean llmSummary;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;

    private final DecidedSource decided = new DecidedSource();
    private final List<String> headings = new ArrayList<>();
    private final List<String> unitSources = new ArrayList<>();
    private final List<String> unitTargets = new ArrayList<>();
    private int acceptedSinceRefresh;
    private int acceptedInUnit;
    private @Nullable String projectId;
    private @Nullable String lastDecidedKey;

    /**
     * Creates the keeper of one run.
     *
     * @param summaries where the summary versions are stored; never null
     * @param glossary the glossary read at each refresh; never null
     * @param templates the prompt templates; never null
     * @param mapper the JSON reader for the model's reply; never null
     * @param frame the run's language pair and style, for the summary call's system message; never null
     * @param dial what the quality dial means for this run; its {@code llmSummary} picks the model-written summary
     * @param calls the seam every model call goes through; never null
     */
    public RollingSummaryKeeper(
            final SummaryRepository summaries,
            final GlossaryRepository glossary,
            final PromptTemplates templates,
            final ObjectMapper mapper,
            final CallFrame frame,
            final DialParameters dial,
            final ModelCalls calls) {
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.modelCall = new SummaryModelCall(
                Objects.requireNonNull(templates, "templates"),
                Objects.requireNonNull(mapper, "mapper"),
                Objects.requireNonNull(frame, "frame"),
                Objects.requireNonNull(calls, "calls"));
        this.llmSummary = Objects.requireNonNull(dial, "dial").llmSummary();
        this.sourceLanguage = frame.sourceLanguage();
        this.targetLanguage = frame.targetLanguage();
    }

    /**
     * Takes note of a segment the run has decided and refreshes the summary when enough have been accepted.
     *
     * @param segment the decided segment; never null
     * @param record its stored decision; never null. Only an {@code ACCEPTED} record counts.
     * @return the new version when a refresh happened, empty when none did, or the error that stopped one
     */
    public Result<Optional<RollingSummary>> onDecided(final Segment segment, final SegmentRecord record) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(record, "record");
        return guarded("decided " + segment.id(), () -> decided(segment, record));
    }

    /**
     * Ends a unit: refreshes the summary, by the model where the dial asks for it, and forgets the unit's texts. An
     * error keeps them, so the run can make the same call again once the person resumes.
     *
     * @param unitId the unit that ended; never null
     * @return the new version when a refresh happened, empty when the unit had no accepted segment or the model's reply
     *     was unreadable, empty or too long for its context (the previous version stays), or the error that stopped it
     */
    public Result<Optional<RollingSummary>> onUnitEnd(final String unitId) {
        Objects.requireNonNull(unitId, "unitId");
        final Result<Optional<RollingSummary>> ended = guarded("unit end " + unitId, () -> unitEnded(unitId));
        if (ended.isOk()) {
            acceptedInUnit = 0;
            unitSources.clear();
            unitTargets.clear();
        } else {
            log.debug("Summary keeper unit end {} kept the unit's texts for the call made again", unitId);
        }
        return ended;
    }

    private Result<Optional<RollingSummary>> decided(final Segment segment, final SegmentRecord record) {
        log.debug("Summary keeper decided segment={} status={}", segment.id(), record.status());
        if (record.status() != SegmentStatus.ACCEPTED) {
            return Result.ok(Optional.empty());
        }
        remember(segment, record);
        if (acceptedSinceRefresh < REFRESH_EVERY) {
            return Result.ok(Optional.empty());
        }
        return refresh(Trigger.COUNT, segment.unit());
    }

    private Result<Optional<RollingSummary>> unitEnded(final String unitId) {
        if (acceptedInUnit == 0) {
            log.debug("Summary keeper unit end {}: no accepted segment, nothing to refresh", unitId);
            return Result.ok(Optional.empty());
        }
        return refresh(Trigger.UNIT_END, unitId);
    }

    private void remember(final Segment segment, final SegmentRecord record) {
        final String source = DisplayText.of(segment.masked());
        decided.add(segment.masked());
        if (segment.kind() == SegmentKind.HEADING && !source.isEmpty()) {
            headings.add(source);
        }
        if (llmSummary) {
            unitSources.add(source);
            unitTargets.add(DisplayText.of(acceptedTarget(record)));
        }
        acceptedSinceRefresh++;
        acceptedInUnit++;
        projectId = record.projectId();
        lastDecidedKey = segment.id();
    }

    private static String acceptedTarget(final SegmentRecord record) {
        final String masked = record.userTarget() != null ? record.maskedUserTarget() : record.maskedMachineTarget();
        return masked != null ? masked : record.effectiveTarget().orElse("");
    }

    private Result<Optional<RollingSummary>> refresh(final Trigger trigger, final String unitId) {
        final String project = Objects.requireNonNull(projectId, "projectId");
        final Result<Optional<RollingSummary>> stored = summaries.latest(project);
        if (stored.isErr()) {
            return stored;
        }
        final Optional<RollingSummary> previous = Objects.requireNonNull(stored.data(), "stored");
        final Result<List<GlossaryEntry>> entries = glossary.all(project);
        if (entries.isErr()) {
            return Result.err(Objects.requireNonNull(entries.error(), "error"));
        }
        final SummaryText text = condensed(Objects.requireNonNull(entries.data(), "entries"));
        final Result<Optional<String>> target = targetFor(trigger, previous);
        if (target.isErr()) {
            return Result.err(Objects.requireNonNull(target.error(), "error"));
        }
        final Optional<String> written = Objects.requireNonNull(target.data(), "target");
        if (written.isEmpty()) {
            return Result.ok(Optional.empty());
        }
        return save(trigger, unitId, previous, text, written.get());
    }

    private Result<Optional<String>> targetFor(final Trigger trigger, final Optional<RollingSummary> previous) {
        final String held = previous.map(RollingSummary::target).orElse("");
        if (trigger != Trigger.UNIT_END || !llmSummary) {
            return Result.ok(Optional.of(held));
        }
        final Result<Optional<String>> asked = modelCall.summarize(
                previous.map(RollingSummaryKeeper::textOf).orElse(""),
                ChapterText.capped(unitSources, sourceLanguage, ChapterText.MAX_TOKENS),
                ChapterText.capped(unitTargets, targetLanguage, ChapterText.MAX_TOKENS));
        if (asked.isErr()) {
            return unanswered(Objects.requireNonNull(asked.error(), "error"));
        }
        if (Objects.requireNonNull(asked.data(), "asked").isEmpty()) {
            log.warn("Summary reply unreadable or empty, previous version kept");
        }
        return asked;
    }

    // Design D3: an empty or over-long reply never fails a run, so the summary keeps its previous version, as the
    // judge keeps its verdict unread. Every other error is the run's to route — a pause, a stop or a failure.
    private static Result<Optional<String>> unanswered(final AppError error) {
        if (error.code() == ErrorCode.emptyCompletion || error.code() == ErrorCode.contextWindow) {
            log.warn("Summary call gave no summary, previous version kept code={}", error.code());
            return Result.ok(Optional.empty());
        }
        log.debug("Summary call failed code={}; the run routes it", error.code());
        return Result.err(error);
    }

    private static String textOf(final RollingSummary summary) {
        return summary.target().isBlank() ? summary.source() : summary.target();
    }

    private Result<Optional<RollingSummary>> save(
            final Trigger trigger,
            final String unitId,
            final Optional<RollingSummary> previous,
            final SummaryText text,
            final String target) {
        final int version = previous.map(RollingSummary::version).orElse(0) + 1;
        final RollingSummary next = new RollingSummary(
                Objects.requireNonNull(projectId, "projectId"),
                unitId,
                text.text(),
                target,
                version,
                lastDecidedKey,
                acceptedSinceRefresh);
        final Result<RollingSummary> saved = summaries.save(next);
        if (saved.isErr()) {
            return Result.err(Objects.requireNonNull(saved.error(), "error"));
        }
        acceptedSinceRefresh = 0;
        log.debug(
                "Summary refreshed trigger={} version={} tokens={} headingsDropped={} entriesDropped={} mode={}",
                trigger,
                version,
                text.estimatedTokens(),
                text.headingsDropped(),
                text.entriesDropped(),
                trigger == Trigger.UNIT_END && llmSummary ? "model" : "deterministic");
        log.trace("Summary text {}", text.text());
        return Result.ok(Optional.of(Objects.requireNonNull(saved.data(), "saved")));
    }

    private SummaryText condensed(final List<GlossaryEntry> entries) {
        final List<String> lines = entries.stream()
                .map(entry -> new Counted(entry, decided.occurrences(entry.term())))
                .filter(counted -> counted.count() > 0)
                .sorted(Comparator.comparingInt(Counted::count)
                        .reversed()
                        .thenComparing(counted -> counted.entry().term()))
                .map(counted -> line(counted.entry()))
                .toList();
        return SummaryText.build(lines, headings);
    }

    private static String line(final GlossaryEntry entry) {
        final String target = entry.target();
        final String rendering = target == null || target.isBlank() ? "" : " → " + target;
        return "%s%s (%s, %s)"
                .formatted(
                        entry.term(),
                        rendering,
                        entry.type().name().toLowerCase(Locale.ROOT),
                        entry.gender().name().toLowerCase(Locale.ROOT));
    }

    private Result<Optional<RollingSummary>> guarded(
            final String what, final Supplier<Result<Optional<RollingSummary>>> body) {
        try {
            return body.get();
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Summary failed", "The rolling summary could not be refreshed.", null, cause);
            log.error("Unexpected rolling-summary failure at {} code={}", what, error.code(), cause);
            return Result.err(error);
        }
    }

    private record Counted(GlossaryEntry entry, int count) {}
}
