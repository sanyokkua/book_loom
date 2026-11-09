package ua.bookloom.pipeline.revision;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.memory.CheckedParagraphs;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.proofread.GenderWaits;
import ua.bookloom.pipeline.proofread.RevisionWriter;
import ua.bookloom.pipeline.proofread.TermSweep;
import ua.bookloom.pipeline.review.RetryDraft;

/**
 * Backward revision, shared by the Max run's last stage and by Export: first the deterministic sweep of every renamed
 * locked term, then one revision call per segment whose characters' gender became known, then a check of every repaired, flagged or
 * noted paragraph against the translated paragraphs around it. A machine target is replaced
 * and the segment becomes REVISED; the person's own edit is never overwritten — it gets a proposal instead. The sweep makes no model call, so a pause asked for during it
 * waits for the first revision call, where the caller's model seam answers it.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ConsistencyPass {

    private final DocumentPort documents;
    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;
    private final GlossaryRepository glossary;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final RetryDraft retryDraft;
    private final LexiconRepository lexicon;
    private final CheckedParagraphs checked;

    /**
     * Runs the pass over a project's open deferrals.
     *
     * @param projectId the project to revise; never null
     * @param calls the seam revision calls go through, or null when no model is at hand — then every gender deferral
     *     stays open for a later pass
     * @return what changed; {@code validation} when the project is not stored, names no target language or its book
     *     is not open; a storage error; or the error a revision call answered other than an unreadable reply
     */
    public Result<ConsistencyReport> run(final String projectId, @Nullable final ModelCalls calls) {
        return run(projectId, calls, PassOptions.BACKWARD_REVISION);
    }

    /**
     * Runs the pass with the chosen model steps.
     *
     * @param projectId the project to revise; never null
     * @param calls the seam every call goes through, or null when no model is at hand — then only the sweep runs
     * @param options which model steps run besides the gender re-render; never null
     * @return as {@link #run(String, ModelCalls)}; a cancel ends it with {@code cancelled}, every change stored
     *     before it kept
     */
    public Result<ConsistencyReport> run(
            final String projectId, @Nullable final ModelCalls calls, final PassOptions options) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(options, "options");
        log.debug(
                "Backward revision requested project={} withModel={} retryDoubted={} everySegment={}",
                projectId,
                calls != null,
                options.reviseDoubted(),
                options.everySegment());
        try {
            return inputs(projectId).flatMap(inputs -> runWith(inputs, calls, options));
        } catch (Exception cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Backward revision failed",
                    "An unexpected failure stopped the consistency pass.",
                    null,
                    cause);
            log.error("Unexpected backward revision failure project={}", projectId, cause);
            return Result.err(error);
        }
    }

    /**
     * What still waits on the person without running the pass: the open deferrals, and the characters whose unknown
     * gender holds segments back. Nothing is changed and no model is asked.
     *
     * @param projectId the project; never null
     * @return a report that changed nothing and names the open deferrals; or the storage error that read them
     */
    public Result<ConsistencyReport> waiting(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return deferrals.open(projectId).flatMap(open -> {
                log.debug("Open deferrals read without the pass project={} deferrals={}", projectId, byReason(open));
                return glossary.all(projectId)
                        .map(entries -> new PassTally().report(byReason(open), GenderWaits.of(open, entries)));
            });
        } catch (Exception cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Open deferrals unreadable",
                    "An unexpected failure stopped reading what waits on the person.",
                    null,
                    cause);
            log.error("Unexpected failure reading open deferrals project={}", projectId, cause);
            return Result.err(error);
        }
    }

    private Result<ConsistencyReport> runWith(
            final PassInputs inputs, @Nullable final ModelCalls calls, final PassOptions options) {
        final Result<List<Deferral>> read = deferrals.open(inputs.projectId());
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), ERROR));
        }
        final List<Deferral> open = Objects.requireNonNull(read.data(), "open");
        log.info("Backward revision started project={} deferrals={}", inputs.projectId(), byReason(open));
        final PassTally tally = new PassTally();
        final RevisionWriter writer = new RevisionWriter(segments, deferrals);
        return new TermSweep(glossary, segments, writer)
                .sweep(SweepAdapter.inputsOf(inputs), open, SweepAdapter.countingIn(inputs, tally))
                .flatMap(swept -> deferrals.open(inputs.projectId()))
                .flatMap(afterSweep -> revise(inputs, afterSweep, calls, writer, tally, options))
                .flatMap(done -> deferrals.open(inputs.projectId()))
                .flatMap(stillOpen -> glossary.all(inputs.projectId())
                        .map(entries -> ended(inputs.projectId(), tally, stillOpen, entries)));
    }

    private Result<Boolean> revise(
            final PassInputs inputs,
            final List<Deferral> open,
            @Nullable final ModelCalls calls,
            final RevisionWriter writer,
            final PassTally tally,
            final PassOptions options) {
        if (calls == null) {
            final long waiting = open.stream()
                    .filter(deferral -> deferral.reason() == DeferralReason.GENDER_UNKNOWN)
                    .count();
            log.info(
                    "Backward revision project={} left {} gender deferrals open: no model to re-render them",
                    inputs.projectId(),
                    waiting);
            return Result.ok(false);
        }
        final RevisionCall call = new RevisionCall(templates, new DraftReplyParser(mapper));
        return new GenderRevision(glossary, segments, deferrals, writer, call)
                .revise(inputs, open, calls, tally)
                .flatMap(revised -> doubted(inputs, options))
                .flatMap(doubted -> retryDoubted(inputs, doubted, calls, options, tally)
                        .flatMap(retried -> new NeighbourRevision(segments, writer, call, checked)
                                .revise(
                                        inputs,
                                        calls,
                                        tally,
                                        new NeighbourRevision.Scope(
                                                Set.copyOf(doubted.keySet()), options.everySegment()))));
    }

    // The audit reads the records as they stand once the gender step has run, so a segment it re-rendered is judged
    // on its new text; the ids are read once, so the neighbour check still reads a segment the retry improved.
    private Result<Map<String, List<QaFinding>>> doubted(final PassInputs inputs, final PassOptions options) {
        if (!options.reviseDoubted()) {
            log.debug("Doubted segments not revised project={}", inputs.projectId());
            return Result.ok(Map.of());
        }
        return inOrder(inputs).map(records -> {
            final Map<String, List<QaFinding>> all =
                    RetryPass.doubted(records, FinalAudit.scan(inputs.book(), records));
            final Map<String, List<QaFinding>> doubted = new LinkedHashMap<>(all);
            doubted.keySet().removeIf(id -> !inputs.isStory(id));
            log.debug(
                    "Doubted segments project={} count={} skippedAsMatter={}",
                    inputs.projectId(),
                    doubted.size(),
                    all.size() - doubted.size());
            return doubted;
        });
    }

    private Result<Boolean> retryDoubted(
            final PassInputs inputs,
            final Map<String, List<QaFinding>> doubted,
            final ModelCalls calls,
            final PassOptions options,
            final PassTally tally) {
        return doubted.isEmpty()
                ? Result.ok(false)
                : new RetryPass(segments, retryDraft, checked, options.profile()).retry(inputs, doubted, calls, tally);
    }

    private Result<List<SegmentRecord>> inOrder(final PassInputs inputs) {
        return segments.all(inputs.projectId()).map(records -> {
            final Map<String, SegmentRecord> byId = new HashMap<>();
            records.forEach(record -> byId.put(record.segmentId(), record));
            return inputs.order().stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .toList();
        });
    }

    private static ConsistencyReport ended(
            final String projectId,
            final PassTally tally,
            final List<Deferral> open,
            final List<GlossaryEntry> entries) {
        final Map<DeferralReason, Integer> stillOpen = byReason(open);
        final ConsistencyReport report = tally.report(stillOpen, GenderWaits.of(open, entries));
        log.info(
                "Backward revision ended project={} segmentsChanged={} termSubstitutions={} genderReRenders={} "
                        + "neighbourFixes={} proposals={} openDeferrals={}",
                projectId,
                tally.segmentsChanged(),
                report.termSubstitutions(),
                report.genderReRenders(),
                report.neighbourFixes(),
                report.proposals(),
                stillOpen);
        return report;
    }

    private Result<PassInputs> inputs(final String projectId) {
        return new PassReader(documents, openProjects, projects, glossary, lexicon).read(projectId);
    }

    private static Map<DeferralReason, Integer> byReason(final List<Deferral> open) {
        final Map<DeferralReason, Set<String>> segments = new EnumMap<>(DeferralReason.class);
        open.forEach(deferral -> segments.computeIfAbsent(deferral.reason(), reason -> new HashSet<>())
                .add(deferral.segmentId()));
        final Map<DeferralReason, Integer> counts = new EnumMap<>(DeferralReason.class);
        segments.forEach((reason, ids) -> counts.put(reason, ids.size()));
        return counts;
    }
}
