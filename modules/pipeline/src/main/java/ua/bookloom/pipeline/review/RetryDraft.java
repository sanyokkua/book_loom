package ua.bookloom.pipeline.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.PromptFrameRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.BookRules;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.ModelWindows;
import ua.bookloom.pipeline.context.RunFrame;
import ua.bookloom.pipeline.context.RunFrames;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.review.RetryRequests.Note;
import ua.bookloom.pipeline.review.RetryRequests.Retried;
import ua.bookloom.pipeline.run.PromptRequests;
import ua.bookloom.pipeline.run.RunSettings;

/**
 * Retry and Retry with note: one fair second attempt at a FLAGGED or ACCEPTED segment (design D8). It builds the draft's
 * context the moment it is asked, the way a run builds one: the run frame over the current glossary, recurring terms
 * and brief, stored once per project; the window of the stored targets the person and the run kept before the segment
 * and the next source; and the map as it stands — never replaying the first draft's snapshot. The draft is a batch of
 * one through the run's own prompts, in the same pieces when the segment is alone above the budget, each carrying the
 * note, and it is decided by the run's draft step, checks, reviewer and acceptance rule — the quality loop with no
 * repair. The record keeps the sections the retry sent, marked as a retry's. It never queues behind a running book:
 * while the latest run is RUNNING it answers {@code busy} before any call. A failure never downgrades an ACCEPTED
 * segment. The consistency pass reuses the draft and decision alone ({@link #candidate}) and stores by its own rule.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class RetryDraft {

    private final DocumentPort documents;
    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;
    private final PromptFrameRepository frames;
    private final RunRepository runs;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;
    private final Provider<ReviewMode> mode;
    private final Clock clock;

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
     *     status, or a project with no open book; or the error a store or a model call answered
     */
    public Result<SegmentRecord> retry(
            final String projectId,
            final String segmentId,
            @Nullable final String note,
            final boolean lowerTemperature,
            final ChatModel model) {
        return retry(projectId, segmentId, note, lowerTemperature, model, event -> {});
    }

    /**
     * Drafts one segment again, announcing each model call to {@code progress} as a run does.
     *
     * @param progress the non-null receiver of the announcements, called on the calling thread
     * @see #retry(String, String, String, boolean, ChatModel)
     */
    public Result<SegmentRecord> retry(
            final String projectId,
            final String segmentId,
            @Nullable final String note,
            final boolean lowerTemperature,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        final Note asked = new Note(note == null ? "" : note.strip(), lowerTemperature);
        log.debug(
                "retry requested project={} segment={} note={} lowerTemperature={}",
                projectId,
                segmentId,
                !asked.text().isEmpty(),
                lowerTemperature);
        if (log.isTraceEnabled()) {
            log.trace("retry segment={} note={}", segmentId, asked.text());
        }
        final Target target = new Target(model.profile(), ModelWindows.of(model, null));
        return load(projectId, segmentId)
                .flatMap(this::retryable)
                .flatMap(this::notRunning)
                .flatMap(record -> plan(record, target))
                .flatMap(plan -> drafted(
                                plan,
                                asked,
                                RetryCalls.over(
                                        model, progress, clock, plan.callFrame().targetLanguage(), plan.document()))
                        .flatMap(retried -> new RetryStore(segments).store(plan.record(), retried)));
    }

    /**
     * Drafts one segment again and decides the draft as {@link #retry} does, but stores nothing and does not check for
     * a running book: the caller owns both choices.
     *
     * @param record the non-null stored record to draft again, FLAGGED or ACCEPTED
     * @param calls the non-null seam every model call is sent through
     * @param profile the non-null profile of the model {@code calls} reach, which sizes the window and the frame
     * @return the decided candidate; {@code validation} for another status or a project with no open book; or the error
     *     a store or a model call answered
     */
    public Result<RetryCandidate> candidate(
            final SegmentRecord record, final ModelCalls calls, final ModelProfile profile) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(profile, "profile");
        log.debug("retry candidate requested segment={} status={}", record.segmentId(), record.status());
        final Target target = new Target(profile, ModelWindow.of(profile.context(), null));
        return retryable(record)
                .flatMap(retryable -> plan(retryable, target))
                .flatMap(plan -> drafted(plan, Note.NONE, calls)
                        .map(retried -> new RetryCandidate(plan.segment(), retried.outcome(), retried.snapshot())));
    }

    private Result<SegmentRecord> retryable(final SegmentRecord record) {
        final SegmentStatus status = record.status();
        log.debug("retry: segment={} status={}", record.segmentId(), status);
        if (status != SegmentStatus.FLAGGED && status != SegmentStatus.ACCEPTED) {
            return refuse(
                    ErrorCode.validation,
                    record.segmentId(),
                    "This segment is " + status.name().toLowerCase(Locale.ROOT)
                            + "; only a flagged or accepted segment can be retried.");
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

    private Result<RetryPlan> plan(final SegmentRecord record, final Target target) {
        final Result<Optional<Project>> found = projects.find(record.projectId());
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error()));
        }
        return Objects.requireNonNull(found.data())
                .map(project -> planFor(record, project.brief(), target))
                .orElseGet(() -> refuse(ErrorCode.validation, record.segmentId(), "This project is not stored."));
    }

    private Result<RetryPlan> planFor(final SegmentRecord record, final BookBrief brief, final Target target) {
        final Document document = openProjects.get(record.projectId());
        final Optional<Segment> segment = document == null ? Optional.empty() : sourceOf(document, record.segmentId());
        log.debug(
                "retry: segment={} bookOpen={} segmentFound={} targetLanguage={}",
                record.segmentId(),
                document != null,
                segment.isPresent(),
                brief.targetLanguage());
        if (document == null || segment.isEmpty()) {
            return refuse(
                    ErrorCode.validation,
                    record.segmentId(),
                    "The book of this project is not open, so the segment cannot be retried.");
        }
        if (brief.targetLanguage() == null) {
            return refuse(ErrorCode.validation, record.segmentId(), "The brief names no language to translate into.");
        }
        final BookIndex index = Objects.requireNonNull(openProjects.index(record.projectId()), "index of an open book");
        final CallFrame callFrame = CallFrame.forRun(
                brief, StyleSheet.from(brief), document, CallFrame.scriptOf(index), WordValidator.none());
        return runFrameOf(record.projectId(), brief, document, index, target.profile())
                .map(runFrame -> new RetryPlan(record, brief, document, segment.get(), callFrame, runFrame, target));
    }

    // The frame a run would start with now, over today's glossary, recurring terms and brief, stored like a run's.
    private Result<RunFrame> runFrameOf(
            final String projectId,
            final BookBrief brief,
            final Document document,
            final BookIndex index,
            final ModelProfile profile) {
        return new RunFrames(templates, new BookRules(templates.languageRules()))
                .buildStored(
                        new RunFrames.FrameStores(glossary, lexicon, frames),
                        projectId,
                        brief,
                        document,
                        index,
                        profile);
    }

    // The run's own prompts over a chunk of the one segment: the masks and the gate from today's glossary, the window
    // and map the run's planner gives, and the reviewer's sheets. The mode is asked once per retry, so a change in
    // Settings reaches the next retry and one retry never mixes two.
    private Result<Retried> drafted(final RetryPlan plan, final Note note, final ModelCalls calls) {
        final TranslatePlanner planner = plannerOf(plan);
        final RunSettings settings = settingsOf(plan);
        final BookFormat format = plan.document().format();
        final GateFunction documentGate = GateFunction.of(documents, format);
        log.debug(
                "retry: drafting segment={} window={} dial={} mode={} frame={}",
                plan.segment().id(),
                settings.window(),
                plan.brief().dial(),
                settings.mode(),
                plan.runFrame().frameHash());
        return PromptRequests.read(
                        glossary,
                        lexicon,
                        settings,
                        new Chunk(plan.record().unitId(), List.of(plan.segment()), false),
                        documentGate,
                        new SegmentTranslator(documentGate, calls, format, planner, mapper),
                        drafterOf(planner, plan.callFrame(), calls),
                        planner)
                .flatMap(requests -> new RetryRequests(requests, segments, splitter, qualityLoop)
                        .draft(plan.segment(), plan.runFrame().frameHash(), note, settings, calls));
    }

    private TranslatePlanner plannerOf(final RetryPlan plan) {
        final int window = plan.target().window();
        return new TranslatePlanner(
                new ContextBuilder(templates),
                plan.runFrame(),
                plan.target().profile(),
                () -> window,
                plan.document().units());
    }

    private RunSettings settingsOf(final RetryPlan plan) {
        final BookBrief brief = plan.brief();
        return new RunSettings(
                plan.record().projectId(),
                mode.get(),
                DialParameters.of(brief.dial()),
                plan.callFrame(),
                brief.names(),
                plan.target().window());
    }

    // The drafter is what the run's prompts are read with; a retry of one segment never batches.
    private BatchDrafter drafterOf(final TranslatePlanner planner, final CallFrame frame, final ModelCalls calls) {
        return new BatchDrafter(
                new BatchPromptBuilder(planner),
                new BatchReplyParser(mapper),
                calls,
                frame.sourceLanguage(),
                frame.targetLanguage(),
                BatchDrafter.NO_BATCHING);
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

    private static Optional<Segment> sourceOf(final Document document, final String segmentId) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.id().equals(segmentId))
                .findFirst();
    }

    private static <T> Result<T> refuse(final ErrorCode code, final String segmentId, final String message) {
        log.warn("retry refused segment={} code={}", segmentId, code);
        return Result.err(AppError.of(code, "Cannot retry", message));
    }

    /**
     * The model a retry's calls go to, as far as its prompts are sized for it.
     *
     * @param profile the model's profile, which chooses the frame's examples and the window's previous pairs
     * @param window the context window in tokens every call of the retry is sized for; positive
     */
    private record Target(ModelProfile profile, int window) {}

    /**
     * What one retry is built from: the stored record, the brief, the opened book and segment, the call frame of the
     * checks, the run frame the prompts are planned with, and the model they are sized for.
     */
    private record RetryPlan(
            SegmentRecord record,
            BookBrief brief,
            Document document,
            Segment segment,
            CallFrame callFrame,
            RunFrame runFrame,
            Target target) {}
}
