package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.heal.DraftEvaluation;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.LoopSettings;

/**
 * A segment's three announcements — started, drafted and decided — which the Translating screen's live panel, tiles
 * and log are built from alone. The book text they carry stays in memory: it is written to the log at TRACE only,
 * and the lines above TRACE name the segment by its id.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class SegmentEvents {

    private final Consumer<JobEvent> emit;
    private final Map<String, SegmentLocator> locators;

    /**
     * Creates the announcer of one run.
     *
     * @param emit the non-null receiver of each event
     * @param locators the non-null, unmodifiable locator of every segment of the opened book, the one a memory reuse
     *     is named by
     */
    SegmentEvents(final Consumer<JobEvent> emit, final Map<String, SegmentLocator> locators) {
        this.emit = Objects.requireNonNull(emit, "emit");
        this.locators = Objects.requireNonNull(locators, "locators");
    }

    /**
     * Announces a segment before its first call or its memory reuse.
     *
     * @param segment the segment, one of the opened book's
     * @param position its section and the chunk the run is in
     */
    void started(final Segment segment, final ChunkPosition position) {
        final SegmentLocator locator =
                Objects.requireNonNull(locators.get(segment.id()), () -> "no locator for " + segment.id());
        final String displaySource = DisplayText.of(segment.masked());
        log.debug("Sending SegmentStarted segmentId={} locator={} position={}", segment.id(), locator.text(), position);
        if (log.isTraceEnabled()) {
            log.trace("Started segment text segmentId={} displaySource={}", segment.id(), displaySource);
        }
        emit.accept(new SegmentStarted(segment.id(), locator.text(), displaySource, position));
    }

    /**
     * Announces a draft whose markup restored, with the confidence its first evaluation gives it.
     *
     * @param outcome the draft step's outcome; one flagged at once, one that failed the gate, or a memory reuse is
     *     not a draft to show and announces nothing
     * @param settings the chunk's loop settings the draft is evaluated with
     */
    void drafted(final DraftOutcome outcome, final LoopSettings settings) {
        switch (outcome) {
            case DraftOutcome.Drafted drafted -> draftedReply(drafted, settings);
            case DraftOutcome.FlaggedAtOnce flagged ->
                log.debug(
                        "No SegmentDrafted segmentId={}: flagged at once",
                        flagged.segment().id());
            case DraftOutcome.Reused reused ->
                log.debug(
                        "No SegmentDrafted segmentId={}: reused from memory",
                        reused.segment().id());
        }
    }

    private void draftedReply(final DraftOutcome.Drafted drafted, final LoopSettings settings) {
        final String maskedForm = drafted.maskedForm();
        if (maskedForm == null) {
            log.debug(
                    "No SegmentDrafted segmentId={}: the draft failed the gate",
                    drafted.segment().id());
            return;
        }
        final double confidence = DraftEvaluation.evaluate(drafted, settings).confidence();
        final String displayTarget = DisplayText.of(maskedForm);
        log.debug(
                "Sending SegmentDrafted segmentId={} confidence={}",
                drafted.segment().id(),
                confidence);
        if (log.isTraceEnabled()) {
            log.trace(
                    "Drafted segment text segmentId={} displayTarget={}",
                    drafted.segment().id(),
                    displayTarget);
        }
        emit.accept(new SegmentDrafted(drafted.segment().id(), displayTarget, confidence));
    }

    /**
     * Announces a decision with what lay behind it.
     *
     * @param record the decided record
     * @param reason the flagged record's typed reason, or {@code null} for an accepted one
     * @param progress the snapshot after the decision
     */
    void decided(final SegmentRecord record, @Nullable final ErrorCode reason, final JobProgress progress) {
        final List<String> kinds =
                record.findings().stream().map(QaFinding::kind).distinct().toList();
        final SegmentDetail detail = new SegmentDetail(record.judgeScore(), record.path(), kinds);
        log.debug(
                "Sending SegmentDecided segmentId={} status={} judgeScore={} path={} findingKinds={}",
                record.segmentId(),
                record.status(),
                record.judgeScore(),
                record.path(),
                kinds);
        emit.accept(new SegmentDecided(record.segmentId(), record.status(), reason, progress, detail));
    }
}
