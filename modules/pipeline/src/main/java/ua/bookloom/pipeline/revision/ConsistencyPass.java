package ua.bookloom.pipeline.revision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.CheckedParagraphs;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.review.RetryDraft;
import ua.bookloom.pipeline.typography.TypographyGate;

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
    private final SummaryRepository summaries;
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
        } catch (Throwable cause) {
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

    private Result<ConsistencyReport> runWith(
            final PassInputs inputs, @Nullable final ModelCalls calls, final PassOptions options) {
        final Result<List<Deferral>> read = deferrals.open(inputs.projectId());
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        final List<Deferral> open = Objects.requireNonNull(read.data(), "open");
        log.info("Backward revision started project={} deferrals={}", inputs.projectId(), byReason(open));
        final PassTally tally = new PassTally();
        final RevisionWriter writer = new RevisionWriter(segments, deferrals);
        return new TermSweep(glossary, segments, writer)
                .sweep(inputs, open, tally)
                .flatMap(swept -> deferrals.open(inputs.projectId()))
                .flatMap(afterSweep -> revise(inputs, afterSweep, calls, writer, tally, options))
                .flatMap(done -> deferrals.open(inputs.projectId()))
                .map(stillOpen -> ended(inputs.projectId(), tally, stillOpen));
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
                .flatMap(doubted -> retryDoubted(inputs, doubted, calls, tally)
                        .flatMap(retried -> new NeighbourRevision(segments, writer, call, checked)
                                .revise(
                                        inputs,
                                        calls,
                                        tally,
                                        new NeighbourRevision.Scope(Set.copyOf(doubted), options.everySegment()))));
    }

    // The audit reads the records as they stand once the gender step has run, so a segment it re-rendered is judged
    // on its new text; the ids are read once, so the neighbour check still reads a segment the retry improved.
    private Result<List<String>> doubted(final PassInputs inputs, final PassOptions options) {
        if (!options.reviseDoubted()) {
            log.debug("Doubted segments not revised project={}", inputs.projectId());
            return Result.ok(List.of());
        }
        return inOrder(inputs).map(records -> {
            final List<String> doubted = RetryPass.doubted(records, FinalAudit.scan(inputs.book(), records));
            log.debug("Doubted segments project={} count={}", inputs.projectId(), doubted.size());
            return doubted;
        });
    }

    private Result<Boolean> retryDoubted(
            final PassInputs inputs, final List<String> doubted, final ModelCalls calls, final PassTally tally) {
        return doubted.isEmpty()
                ? Result.ok(false)
                : new RetryPass(segments, retryDraft).retry(inputs, doubted, calls, tally);
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

    private static ConsistencyReport ended(final String projectId, final PassTally tally, final List<Deferral> open) {
        final Map<DeferralReason, Integer> stillOpen = byReason(open);
        final ConsistencyReport report = tally.report(stillOpen, awaitingGender(open));
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
        final Result<Optional<Project>> found = projects.find(projectId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final BookBrief brief = Objects.requireNonNull(found.data(), "found")
                .map(Project::brief)
                .orElse(null);
        final Document document = openProjects.get(projectId);
        final String target = brief == null ? null : brief.targetLanguage();
        log.debug(
                "Backward revision inputs project={} stored={} bookOpen={} targetLanguage={}",
                projectId,
                brief != null,
                document != null,
                target);
        if (brief == null || document == null || target == null || target.isBlank()) {
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Nothing to revise",
                    "The project must be stored, name a target language and have its book open to be revised."));
        }
        return bookContext(projectId)
                .flatMap(context ->
                        glossary.all(projectId).map(entries -> inputsOf(projectId, brief, document, entries, context)));
    }

    private Result<BookContext> bookContext(final String projectId) {
        final Result<Optional<RollingSummary>> summary = summaries.latest(projectId);
        if (summary.isErr()) {
            return Result.err(Objects.requireNonNull(summary.error(), "error"));
        }
        final String text = Objects.requireNonNull(summary.data(), "summary")
                .map(RollingSummary::target)
                .filter(target -> !target.isBlank())
                .orElse(null);
        log.debug("Backward revision context project={} summary={}", projectId, text != null);
        return lexicon.all(projectId).map(terms -> new BookContext(terms, text));
    }

    private PassInputs inputsOf(
            final String projectId,
            final BookBrief brief,
            final Document document,
            final List<GlossaryEntry> entries,
            final BookContext context) {
        final String target = Objects.requireNonNull(brief.targetLanguage(), "target");
        final String source = brief.sourceLanguage() == null ? document.declaredLang() : brief.sourceLanguage();
        final Map<String, Segment> sources = new LinkedHashMap<>();
        document.units().forEach(unit -> unit.segments().forEach(segment -> sources.put(segment.id(), segment)));
        return new PassInputs(
                projectId,
                new CallFrame(source, target, StyleSheet.from(brief), brief.foreignPassages(), null, brief.narrator()),
                brief.names(),
                TypographyGate.around(GateFunction.of(documents, document.format()), target),
                sources,
                SegmentLocators.of(document),
                entries,
                List.copyOf(sources.keySet()),
                new FinalAudit.Book(brief, document, entries, WordValidator.none()),
                context);
    }

    // Counted by segment: a segment naming two unknown-gender characters holds two deferrals but is one segment that
    // waits, and the export message says "segments".
    // One entry per character still unknown, with the segments that wait on it; a segment waiting on two characters
    // counts for each, because setting either one is a reason to look at it.
    private static List<GenderWait> awaitingGender(final List<Deferral> open) {
        final Map<String, Set<String>> byCharacter = new HashMap<>();
        open.stream()
                .filter(deferral -> deferral.reason() == DeferralReason.GENDER_UNKNOWN && deferral.waitingOn() != null)
                .forEach(deferral -> byCharacter
                        .computeIfAbsent(Objects.requireNonNull(deferral.waitingOn()), name -> new HashSet<>())
                        .add(deferral.segmentId()));
        return byCharacter.entrySet().stream()
                .map(entry -> new GenderWait(entry.getKey(), entry.getValue().size()))
                .sorted(Comparator.comparingInt(GenderWait::segments).reversed().thenComparing(GenderWait::character))
                .toList();
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
