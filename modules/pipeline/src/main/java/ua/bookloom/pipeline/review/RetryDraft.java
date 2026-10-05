package ua.bookloom.pipeline.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.context.ContextPackageAssembler;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.ChunkBudget;
import ua.bookloom.pipeline.run.OutcomeRecords;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * Retry and Retry with note: one fair second attempt at a FLAGGED or ACCEPTED segment. It replays the context its first
 * draft saw, from the stored snapshot's texts alone, and is decided by the run's own draft step, checks, reviewer and
 * acceptance rule — the quality loop with no repair round. A segment the run drafted in pieces is drafted in the same
 * pieces, each carrying the note. It never queues behind a running book: while the project's
 * latest run is RUNNING it answers {@code busy} before any call. A failure never downgrades an ACCEPTED segment.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class RetryDraft {

    private static final String RETRY = "retry";

    private final DocumentPort documents;
    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final RunRepository runs;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;
    private final ReviewMode mode;

    /**
     * Drafts one segment again.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @param note the person's instruction shown under {@code [Extra instruction]}, or null for none
     * @param lowerTemperature whether the draft call asks for the draft's lower temperature
     * @param model the non-null bound model, called directly since no run is using it
     * @return the stored record after the retry — ACCEPTED and reviewed on a pass, FLAGGED with the new findings on a
     *     failing FLAGGED segment — or, for a failing ACCEPTED segment, its unchanged stored record carrying the new
     *     findings; {@code busy} while a run of the project is RUNNING; {@code validation} for an unknown id, another
     *     status, a record with no snapshot to replay, or a project with no open book; or the error a model call
     *     answered
     */
    public Result<SegmentRecord> retry(
            final String projectId,
            final String segmentId,
            @Nullable final String note,
            final boolean lowerTemperature,
            final ChatModel model) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(model, "model");
        final String instruction = note == null ? "" : note.strip();
        if (log.isTraceEnabled()) {
            log.trace("retry segment={} note={}", segmentId, instruction);
        }
        return load(projectId, segmentId)
                .flatMap(this::retryable)
                .flatMap(this::notRunning)
                .flatMap(this::plan)
                .flatMap(plan -> attempt(plan, instruction, lowerTemperature, model));
    }

    private Result<SegmentRecord> retryable(final SegmentRecord record) {
        final SegmentStatus status = record.status();
        log.debug("retry: segment={} status={} hasSnapshot={}", record.segmentId(), status, record.context() != null);
        if (status != SegmentStatus.FLAGGED && status != SegmentStatus.ACCEPTED) {
            return refuse(
                    ErrorCode.validation,
                    record.segmentId(),
                    "This segment is " + status.name().toLowerCase(Locale.ROOT)
                            + "; only a flagged or accepted segment can be retried.");
        }
        if (record.context() == null) {
            return refuse(
                    ErrorCode.validation,
                    record.segmentId(),
                    "Nothing is recorded of what this segment's first draft saw, so there is nothing to replay.");
        }
        return Result.ok(record);
    }

    private Result<SegmentRecord> notRunning(final SegmentRecord record) {
        final Result<Optional<RunRecord>> latest = runs.latest(record.projectId());
        if (latest.isErr()) {
            return Result.err(Objects.requireNonNull(latest.error()));
        }
        final JobState state =
                Objects.requireNonNull(latest.data()).map(RunRecord::state).orElse(null);
        log.debug("retry segment={} latestRun={}", record.segmentId(), state == null ? "none" : state);
        if (state == JobState.RUNNING) {
            return refuse(
                    ErrorCode.busy,
                    record.segmentId(),
                    "The book is being translated. Pause or stop it to retry this segment.");
        }
        return Result.ok(record);
    }

    private Result<RetryPlan> plan(final SegmentRecord record) {
        final Result<Optional<Project>> found = projects.find(record.projectId());
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error()));
        }
        return Objects.requireNonNull(found.data())
                .map(project -> planFor(record, project.brief()))
                .orElseGet(() -> refuse(ErrorCode.validation, record.segmentId(), "This project is not stored."));
    }

    private Result<RetryPlan> planFor(final SegmentRecord record, final BookBrief brief) {
        final Document document = openProjects.get(record.projectId());
        final Optional<Segment> segment = document == null ? Optional.empty() : sourceOf(document, record.segmentId());
        final String target = brief.targetLanguage();
        log.debug(
                "retry: segment={} bookOpen={} segmentFound={} targetLanguage={}",
                record.segmentId(),
                document != null,
                segment.isPresent(),
                target);
        if (document == null || segment.isEmpty()) {
            return refuse(
                    ErrorCode.validation,
                    record.segmentId(),
                    "The book of this project is not open, so the segment cannot be retried.");
        }
        if (target == null) {
            return refuse(ErrorCode.validation, record.segmentId(), "The brief names no language to translate into.");
        }
        final ContextSnapshot snapshot = Objects.requireNonNull(record.context(), "checked snapshot");
        final CallFrame frame = new CallFrame(
                brief.sourceLanguage(),
                target,
                StyleSheet.ofText(snapshot.styleSheet()),
                brief.foreignPassages(),
                CallFrame.bookLanguageOf(document),
                brief.narrator());
        return Result.ok(new RetryPlan(record, snapshot, brief, frame, document, segment.get()));
    }

    private Result<SegmentRecord> attempt(
            final RetryPlan plan, final String instruction, final boolean lowerTemperature, final ChatModel model) {
        final Segment segment = plan.segment();
        final ContextSnapshot snapshot = plan.snapshot();
        final List<GlossaryEntry> terms = termsOf(snapshot, plan.record().projectId());
        final ProtectedMask mask = ProtectedSpans.mask(segment, plan.frame(), terms);
        final DraftContext context = ContextPackageAssembler.replay(snapshot, mask);
        logReplayed(segment.id(), snapshot);
        final GateFunction gate = TypographyGate.around(
                ProtectedSpans.gate(
                        Map.of(segment.id(), mask),
                        GateFunction.of(documents, plan.document().format())),
                plan.frame().targetLanguage());
        final ModelCalls calls = (kind, id, request) -> model.chat(request);
        final SegmentTranslator translator = new SegmentTranslator(
                gate,
                calls,
                plan.document().format(),
                new DraftPromptBuilder(templates, plan.frame()),
                new DraftReplyParser(mapper));
        return translator
                .translateSplit(segment, context, mask, splitter, budgetOf(plan, terms), instruction, lowerTemperature)
                .flatMap(drafted -> decide(plan, drafted, gate, calls))
                .flatMap(outcome -> store(plan, outcome));
    }

    // The run drafts a segment alone above its unit's chunk budget in sentence-aligned pieces; a retry computes that
    // budget the same way, from the unit and the replayed glossary and summary, so the longest paragraphs still fit.
    private static int budgetOf(final RetryPlan plan, final List<GlossaryEntry> terms) {
        final Segment segment = plan.segment();
        final List<Segment> unit = plan.document().units().stream()
                .filter(candidate -> candidate.id().equals(segment.unit()))
                .findFirst()
                .map(Unit::segments)
                .orElse(List.of(segment));
        final ContextBudget window =
                ChunkBudget.budget(plan.frame(), unit, terms, plan.snapshot().summary(), ContextBudget.DEFAULT_WINDOW);
        final int budget = window.chunkTokens();
        log.debug(
                "retry: segment={} budget={} {} unitSegments={}", segment.id(), budget, window.describe(), unit.size());
        return budget;
    }

    // The run's own quality loop with no repair round: the same evaluation, the same one-pair reviewer (labelled s1)
    // when
    // the brief's dial enables it, and the same acceptance rule.
    private Result<SegmentOutcome> decide(
            final RetryPlan plan, final DraftOutcome drafted, final GateFunction gate, final ModelCalls calls) {
        final BookBrief brief = plan.brief();
        final LoopSettings settings = new LoopSettings(
                mode,
                DialParameters.of(brief.dial()).withoutRepairRounds(),
                plan.frame(),
                brief.names(),
                plan.snapshot().glossary().stream().map(SnapshotTerm::term).toList(),
                plan.snapshot().glossary().stream()
                        .filter(term -> term.target() != null && !term.locked())
                        .map(term -> term.term() + " → " + term.target())
                        .toList());
        log.debug(
                "retry: deciding segment={} dial={} reviewPasses={}",
                plan.segment().id(),
                brief.dial(),
                settings.dial().reviewPasses());
        return qualityLoop.start(List.of(drafted), settings, gate, calls).flatMap(ChunkDecider::nextDecision);
    }

    private Result<SegmentRecord> store(final RetryPlan plan, final SegmentOutcome outcome) {
        final SegmentRecord before = plan.record();
        final ContextSnapshot snapshot = plan.snapshot();
        if (log.isTraceEnabled()) {
            log.trace("retry segment={} target={}", before.segmentId(), outcome.machineTarget());
        }
        if (outcome.status() == SegmentStatus.ACCEPTED) {
            return write(
                    before,
                    current ->
                            OutcomeRecords.decided(current, outcome, snapshot).withReviewed(true));
        }
        if (before.status() == SegmentStatus.ACCEPTED) {
            final SegmentRecord answer = before.withFindings(
                    OutcomeRecords.decided(before, outcome, snapshot).findings());
            log.debug(
                    "retry failed on an accepted segment={}; stored record kept unchanged findings={}",
                    before.segmentId(),
                    answer.findings().size());
            logOutcome(answer);
            return Result.ok(answer);
        }
        return write(before, current -> keepingMachineTarget(current, outcome, snapshot));
    }

    // A retry whose reply never passed the hard gates has no target of its own; the earlier one is kept rather than
    // lost, so the person can still accept it.
    private static SegmentRecord keepingMachineTarget(
            final SegmentRecord current, final SegmentOutcome outcome, final ContextSnapshot snapshot) {
        final SegmentRecord decided =
                OutcomeRecords.decided(current, outcome, snapshot).withReviewed(false);
        log.debug(
                "retry: flagged segment={} newMachineTarget={}", current.segmentId(), outcome.machineTarget() != null);
        return outcome.machineTarget() == null
                ? decided.withMachineTarget(current.machineTarget(), current.maskedMachineTarget())
                : decided;
    }

    private Result<SegmentRecord> write(final SegmentRecord before, final UnaryOperator<SegmentRecord> change) {
        final Result<SegmentRecord> updated = segments.update(before.projectId(), before.segmentId(), change);
        final SegmentRecord after = updated.data();
        if (after != null) {
            logOutcome(after);
        }
        return updated;
    }

    private Result<SegmentRecord> load(final String projectId, final String segmentId) {
        final Result<Optional<SegmentRecord>> found = segments.find(projectId, segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error()));
        }
        return Objects.requireNonNull(found.data())
                .map(Result::ok)
                .orElseGet(() ->
                        refuse(ErrorCode.validation, segmentId, "This project holds no segment " + segmentId + "."));
    }

    private static List<GlossaryEntry> termsOf(final ContextSnapshot snapshot, final String projectId) {
        return snapshot.glossary().stream()
                .map(term -> new GlossaryEntry(
                        term.term(),
                        projectId,
                        term.term(),
                        term.target(),
                        term.type(),
                        term.gender(),
                        term.locked(),
                        term.suggested() ? TargetOrigin.SUGGESTED : TargetOrigin.PERSON))
                .toList();
    }

    private static Optional<Segment> sourceOf(final Document document, final String segmentId) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.id().equals(segmentId))
                .findFirst();
    }

    private static void logReplayed(final String segmentId, final ContextSnapshot snapshot) {
        log.debug(
                "retry context segment={} preceding={} terms={} memoryHits={} summary={}",
                segmentId,
                snapshot.precedingTargets().size(),
                snapshot.glossary().size(),
                snapshot.tmHits().size(),
                snapshot.summary() != null);
    }

    private static void logOutcome(final SegmentRecord record) {
        log.info("retry segment={} status={} reviewed={}", record.segmentId(), record.status(), record.reviewed());
    }

    private static <T> Result<T> refuse(final ErrorCode code, final String segmentId, final String message) {
        log.warn("{} refused segment={} code={}", RETRY, segmentId, code);
        return Result.err(AppError.of(code, "Cannot retry", message));
    }

    /** What one retry replays: the stored record, its snapshot, the brief, the call frame and the opened segment. */
    private record RetryPlan(
            SegmentRecord record,
            ContextSnapshot snapshot,
            BookBrief brief,
            CallFrame frame,
            Document document,
            Segment segment) {}
}
