package ua.bookloom.pipeline.revision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.EnumMap;
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
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
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
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Backward revision requested project={} withModel={}", projectId, calls != null);
        try {
            return inputs(projectId).flatMap(inputs -> runWith(inputs, calls));
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

    private Result<ConsistencyReport> runWith(final PassInputs inputs, @Nullable final ModelCalls calls) {
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
                .flatMap(afterSweep -> revise(inputs, afterSweep, calls, writer, tally))
                .flatMap(done -> deferrals.open(inputs.projectId()))
                .map(stillOpen -> ended(inputs.projectId(), tally, byReason(stillOpen)));
    }

    private Result<Boolean> revise(
            final PassInputs inputs,
            final List<Deferral> open,
            @Nullable final ModelCalls calls,
            final RevisionWriter writer,
            final PassTally tally) {
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
                .flatMap(revised -> new NeighbourRevision(segments, writer, call).revise(inputs, calls, tally));
    }

    private static ConsistencyReport ended(
            final String projectId, final PassTally tally, final Map<DeferralReason, Integer> stillOpen) {
        final ConsistencyReport report = tally.report(stillOpen);
        log.info(
                "Backward revision ended project={} segmentsChanged={} termSubstitutions={} genderReRenders={} "
                        + "proposals={} openDeferrals={}",
                projectId,
                tally.segmentsChanged(),
                report.termSubstitutions(),
                report.genderReRenders(),
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
        return glossary.all(projectId).map(entries -> inputsOf(projectId, brief, target, document, entries));
    }

    private PassInputs inputsOf(
            final String projectId,
            final BookBrief brief,
            final String target,
            final Document document,
            final List<GlossaryEntry> entries) {
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
                List.copyOf(sources.keySet()));
    }

    // Counted by segment: a segment naming two unknown-gender characters holds two deferrals but is one segment that
    // waits, and the export message says "segments".
    private static Map<DeferralReason, Integer> byReason(final List<Deferral> open) {
        final Map<DeferralReason, Set<String>> segments = new EnumMap<>(DeferralReason.class);
        open.forEach(deferral -> segments.computeIfAbsent(deferral.reason(), reason -> new HashSet<>())
                .add(deferral.segmentId()));
        final Map<DeferralReason, Integer> counts = new EnumMap<>(DeferralReason.class);
        segments.forEach((reason, ids) -> counts.put(reason, ids.size()));
        return counts;
    }
}
