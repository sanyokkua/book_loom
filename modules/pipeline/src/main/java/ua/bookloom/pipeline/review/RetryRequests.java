package ua.bookloom.pipeline.review;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.context.AcceptedTargets;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.PromptRequests;
import ua.bookloom.pipeline.run.RunSettings;

/**
 * One retry's draft over the run's prompts for a chunk of its one segment: the window read from the stored targets, the
 * draft (in pieces above the budget), and the decision by the quality loop with no repair. The sections the draft was
 * sent are what the record keeps, so "What the model saw" shows the context rebuilt for this retry.
 */
@Slf4j
final class RetryRequests {

    // A retry has no fresh reading of the translation memory, so it is offered none.
    private static final TmLookup NO_MEMORY = new TmLookup(null, List.of(), List.of());

    private final PromptRequests requests;
    private final SegmentRepository segments;
    private final SentenceSplitter splitter;
    private final QualityLoop qualityLoop;

    RetryRequests(
            final PromptRequests requests,
            final SegmentRepository segments,
            final SentenceSplitter splitter,
            final QualityLoop qualityLoop) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.segments = Objects.requireNonNull(segments, "segments");
        this.splitter = Objects.requireNonNull(splitter, "splitter");
        this.qualityLoop = Objects.requireNonNull(qualityLoop, "qualityLoop");
    }

    /**
     * The person's instruction and temperature choice for one retry.
     *
     * @param text the note shown under {@code [Extra instruction]}; empty for none
     * @param lower whether the draft call asks for its lower temperature
     */
    record Note(String text, boolean lower) {

        /** Rejects a missing text. */
        Note {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A retry's decided draft and the snapshot of the context it was sent.
     *
     * @param outcome the quality loop's decision; ACCEPTED or FLAGGED
     * @param snapshot the frame's key and the user sections the retry sent, marked as a retry's
     */
    record Retried(SegmentOutcome outcome, ContextSnapshot snapshot) {

        /** Rejects a missing part. */
        Retried {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    /**
     * Drafts and decides the segment.
     *
     * @param segment the non-null segment of the chunk
     * @param frameHash the non-null key of the stored run frame the draft is sent with
     * @param note the non-null note and temperature choice
     * @param settings the non-null settings the prompts were read with
     * @param calls the non-null seam every call goes through
     * @return the decided draft with its snapshot, or the error a store, the builder or a call answered
     */
    Result<Retried> draft(
            final Segment segment,
            final String frameHash,
            final Note note,
            final RunSettings settings,
            final ModelCalls calls) {
        final ProtectedMask mask = requests.mask(segment);
        return requests.translateContext(
                        List.of(new PromptRequests.BatchSlot(segment, mask, NO_MEMORY)), stored(settings.projectId()))
                .flatMap(context -> requests.sentPlan(segment, context, mask, KindExtra.note(note.text()))
                        .flatMap(sent -> decided(segment, context, mask, note, settings, calls)
                                .map(outcome -> new Retried(
                                        outcome,
                                        new ContextSnapshot(
                                                frameHash,
                                                PromptRequests.userSections(sent.sections()),
                                                sent.chapterSummaryKeys(),
                                                true)))));
    }

    // The run drafts a segment above its tier's current-part budget in sentence-aligned pieces; a retry cuts at the
    // same budget, so it drafts in the pieces the run would.
    private Result<SegmentOutcome> decided(
            final Segment segment,
            final TranslateContext context,
            final ProtectedMask mask,
            final Note note,
            final RunSettings settings,
            final ModelCalls calls) {
        final int budget = requests.currentPart();
        log.debug(
                "retry: segment={} budget={} previousPairs={} window={}",
                segment.id(),
                budget,
                context.window().previous().size(),
                settings.window());
        return requests.translator()
                .translateSplit(segment, context, mask, splitter, budget, note.text(), note.lower())
                .flatMap(drafted -> decide(segment, drafted, settings, calls));
    }

    // The run's own quality loop with no repair: the same evaluation and the same acceptance rule.
    private Result<SegmentOutcome> decide(
            final Segment segment, final DraftOutcome drafted, final RunSettings settings, final ModelCalls calls) {
        final LoopSettings loop = requests.loopSettings().withoutRepair();
        log.debug("retry: deciding segment={} mode={}", segment.id(), settings.mode());
        return qualityLoop.start(List.of(drafted), loop, requests.gate(), calls).nextDecision();
    }

    // The window's previous pairs show what the run would show: a target the person or the run kept, else the source.
    private AcceptedTargets stored(final String projectId) {
        return segmentId -> {
            final Result<Optional<String>> target =
                    segments.find(projectId, segmentId).map(found -> found.flatMap(AcceptedTargets::of));
            log.debug(
                    "retry: stored target segment={} shown={}",
                    segmentId,
                    target.isOk()
                            && Objects.requireNonNull(target.data(), "data").isPresent());
            return target;
        };
    }
}
