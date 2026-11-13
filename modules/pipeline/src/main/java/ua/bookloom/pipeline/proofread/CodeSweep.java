package ua.bookloom.pipeline.proofread;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.pipeline.heal.GateFunction;

/**
 * The Proofread stage's term sweep: every open TERM deferral is answered by code, with no model call, so a renamed
 * glossary target reaches the decided text at every dial. The run supplies its own document gate and target language,
 * so the sweep reads nothing of the stored brief.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class CodeSweep {

    private final SegmentRepository segments;
    private final DeferralRepository deferrals;
    private final GlossaryRepository glossary;

    /**
     * Sweeps every open TERM deferral of a project.
     *
     * @param projectId the non-null project id
     * @param document the non-null book the decided segments belong to, the run's own even if it was closed meanwhile
     * @param gate the non-null document gate every swept target is restored through
     * @param targetLanguage the non-null BCP 47 tag whose forms a pinned target declines in
     * @return what the sweep changed; a storage or gate error; {@code internal} for an unexpected failure
     */
    public Result<TermSweep.Swept> run(
            final String projectId, final Document document, final GateFunction gate, final String targetLanguage) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(document, "document");
        log.debug("Term sweep requested project={} targetLanguage={}", projectId, targetLanguage);
        try {
            final Result<List<Deferral>> open = deferrals.open(projectId);
            if (open.isErr()) {
                return Result.err(Objects.requireNonNull(open.error(), ERROR));
            }
            final SweepInputs inputs = new SweepInputs(projectId, targetLanguage, gate, sourcesOf(document));
            return new TermSweep(glossary, segments, new RevisionWriter(segments, deferrals))
                    .sweep(inputs, Objects.requireNonNull(open.data(), "open"))
                    .map(swept -> {
                        log.debug(
                                "Term sweep done project={} substitutions={} proposals={}",
                                projectId,
                                swept.substitutions(),
                                swept.proposals());
                        return swept;
                    });
        } catch (Exception cause) {
            log.error("Unexpected term sweep failure project={}", projectId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Term sweep failed",
                    "An unexpected failure stopped the sweep of renamed glossary targets.",
                    null,
                    cause));
        }
    }

    private static Map<String, Segment> sourcesOf(final Document document) {
        final Map<String, Segment> sources = new LinkedHashMap<>();
        document.units().forEach(unit -> unit.segments().forEach(segment -> sources.put(segment.id(), segment)));
        return sources;
    }
}
