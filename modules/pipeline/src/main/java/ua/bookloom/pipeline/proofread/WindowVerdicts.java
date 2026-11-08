package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.heal.AcceptanceRule;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;

/**
 * Turns one window's answer into the segments' verdicts (design D15 "Reply"): an item left out or answered unchanged
 * is UNCHANGED; a rewrite the gates refuse is REJECTED with their reason; a kept rewrite of a machine text replaces it
 * and sets the segment REVISED, with the text, its masked form, the status and the findings before kept for Undo
 * proofread and the first reason it was marked for as the verdict's reason — for a FLAGGED segment only
 * when the rewrite clears every blocking finding; a kept rewrite of the person's own edit, or one that leaves a flagged
 * segment blocked, waits as a proposal, PROPOSED. A kept rewrite settles each gender deferral of its segment whose
 * gender the new text now agrees with.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class WindowVerdicts {

    private final ProofreadJudge judge;
    private final Facts facts;
    // The open deferrals as the committed windows left them, so a later round sees an earlier round's proposals.
    private final List<Deferral> open;

    /**
     * What the verdicts read besides the window.
     *
     * @param projectId the project the window belongs to
     * @param targetLanguage the target language tag, whose gender check settles a deferral
     * @param byTerm the glossary by term, for the gender deferrals' facts
     * @param narrator the brief's narrator as the stage read it
     * @param open the open deferrals of the project as the stage read them, before any window was committed
     */
    record Facts(
            String projectId,
            String targetLanguage,
            Map<String, GlossaryEntry> byTerm,
            Narrator narrator,
            List<Deferral> open) {

        /** Copies the collections. */
        Facts {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(targetLanguage, "targetLanguage");
            byTerm = Map.copyOf(Objects.requireNonNull(byTerm, "byTerm"));
            Objects.requireNonNull(narrator, "narrator");
            open = List.copyOf(Objects.requireNonNull(open, "open"));
        }
    }

    /**
     * One read segment as the window saw it.
     *
     * @param segment the opened book's segment
     * @param record the stored record the window read
     * @param current the text the window showed
     * @param reasons the segment's marks
     */
    record Read(Segment segment, SegmentRecord record, String current, List<String> reasons) {}

    /**
     * What a window decided, to be committed together.
     *
     * @param records the read segments with their verdicts and kept texts
     * @param proposals the proposals added for the desk
     * @param resolved the ids of the deferrals the kept texts settle
     * @param counts how many segments got each outcome
     */
    record Decided(
            List<SegmentRecord> records,
            List<Deferral> proposals,
            List<String> resolved,
            Map<ProofreadOutcome, Integer> counts) {}

    WindowVerdicts(final ProofreadJudge judge, final Facts facts) {
        this.judge = Objects.requireNonNull(judge, "judge");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.open = new ArrayList<>(facts.open());
    }

    /** {@return the open deferrals as the committed windows left them} */
    List<Deferral> open() {
        return List.copyOf(open);
    }

    /**
     * Takes in what a window committed: the deferrals it settled are no longer open and its proposals are.
     *
     * @param decided the non-null decisions just committed
     */
    void committed(final Decided decided) {
        Objects.requireNonNull(decided, "decided");
        open.removeIf(deferral -> decided.resolved().contains(deferral.id()));
        open.addAll(decided.proposals());
        log.debug(
                "Proofread open deferrals now={} resolved={} proposed={}",
                open.size(),
                decided.resolved().size(),
                decided.proposals().size());
    }

    /**
     * Decides each read segment of a window.
     *
     * @param reads the non-null read segments in reading order
     * @param changes the non-null changed texts by segment id
     * @param fingerprint the non-null fingerprint of the window
     * @return what to commit; never null
     */
    Decided decide(final List<Read> reads, final Map<String, String> changes, final String fingerprint) {
        final List<SegmentRecord> records = new ArrayList<>();
        final List<Deferral> proposals = new ArrayList<>();
        final List<String> resolved = new ArrayList<>();
        final Map<ProofreadOutcome, Integer> counts = new EnumMap<>(ProofreadOutcome.class);
        for (final Read read : reads) {
            final @Nullable String rewrite = changes.get(read.segment().id());
            final SegmentRecord decided = rewrite == null
                    ? read.record().withProofread(verdict(fingerprint, ProofreadOutcome.UNCHANGED, ""))
                    : rewritten(read, rewrite, fingerprint, proposals, resolved);
            counts.merge(
                    Objects.requireNonNull(decided.proofread(), "proofread").outcome(), 1, Integer::sum);
            records.add(carried(read.record(), decided));
        }
        return new Decided(records, proposals, resolved, counts);
    }

    private SegmentRecord rewritten(
            final Read read,
            final String rewrite,
            final String fingerprint,
            final List<Deferral> proposals,
            final List<String> resolved) {
        return switch (judge.judge(read.segment(), read.current(), rewrite, read.reasons(), arrived(read))) {
            case ProofreadJudge.Judged.Reject(final String reason) -> {
                log.warn(
                        "Proofread rewrite rejected segmentId={} veto={}",
                        read.segment().id(),
                        ProofreadJudge.vetoOf(reason));
                yield read.record().withProofread(verdict(fingerprint, ProofreadOutcome.REJECTED, reason));
            }
            case ProofreadJudge.Judged.Keep keep -> kept(read, keep, fingerprint, proposals, resolved);
        };
    }

    private SegmentRecord kept(
            final Read read,
            final ProofreadJudge.Judged.Keep keep,
            final String fingerprint,
            final List<Deferral> proposals,
            final List<String> resolved) {
        final SegmentRecord record = read.record();
        final Optional<String> blocked = blockedBy(record, keep);
        if (record.maskedUserTarget() != null || blocked.isPresent()) {
            final String reason = blocked.orElse("the person's edit stays");
            proposals.add(proposal(record, keep.restored(), fingerprint));
            resolved.addAll(earlierProposals(record));
            log.debug("Proofread rewrite proposed segmentId={} reason={}", record.segmentId(), reason);
            return record.withProofread(verdict(fingerprint, ProofreadOutcome.PROPOSED, reason));
        }
        resolved.addAll(
                settled(record.segmentId(), DisplayText.of(keep.restored().maskedForm())));
        log.debug("Proofread rewrite kept segmentId={} statusBefore={}", record.segmentId(), record.status());
        return record.withMachineTarget(
                        keep.restored().restored(), keep.restored().maskedForm())
                .withFindings(keep.qa().findings())
                .withStatus(SegmentStatus.REVISED)
                .withProofread(new ProofreadVerdict(
                        fingerprint,
                        ProofreadOutcome.KEPT,
                        read.reasons().isEmpty() ? "" : read.reasons().getFirst(),
                        record.machineTarget(),
                        record.status(),
                        record.maskedMachineTarget(),
                        record.findings()));
    }

    // A FLAGGED segment is cleared only when the rewrite leaves no blocking finding.
    private static Optional<String> blockedBy(final SegmentRecord record, final ProofreadJudge.Judged.Keep keep) {
        if (record.status() != SegmentStatus.FLAGGED || AcceptanceRule.accepts(keep.qa(), 0)) {
            return Optional.empty();
        }
        final List<String> blockers = keep.qa().findings().stream()
                .filter(finding -> CheckLevels.of(finding) == CheckLevel.BLOCK)
                .map(QaFinding::raisedBy)
                .distinct()
                .toList();
        return Optional.of(blockers.isEmpty() ? "still blocked" : "still blocked: " + String.join(", ", blockers));
    }

    private Deferral proposal(
            final SegmentRecord record, final GateResult.Restored restored, final String fingerprint) {
        return new Deferral(
                facts.projectId() + ":" + record.segmentId() + ":" + DeferralReason.PROOFREAD.name() + ":"
                        + fingerprint,
                facts.projectId(),
                record.segmentId(),
                DeferralReason.PROOFREAD,
                null,
                null,
                restored.restored(),
                restored.maskedForm());
    }

    // A new proposal supersedes an earlier proofread proposal of the same segment.
    private List<String> earlierProposals(final SegmentRecord record) {
        return open.stream()
                .filter(deferral -> deferral.segmentId().equals(record.segmentId()))
                .filter(deferral -> deferral.reason() == DeferralReason.PROOFREAD)
                .map(Deferral::id)
                .toList();
    }

    private List<ArrivedGender> arrived(final Read read) {
        return open.stream()
                .filter(deferral -> deferral.segmentId().equals(read.segment().id()))
                .flatMap(deferral -> ArrivedGender.of(deferral, facts.byTerm(), facts.narrator()).stream())
                .toList();
    }

    // Each gender deferral of the segment whose gender is known now and that the kept text agrees with.
    private List<String> settled(final String segmentId, final String plain) {
        final List<String> ids = open.stream()
                .filter(deferral -> deferral.segmentId().equals(segmentId))
                .filter(deferral -> ArrivedGender.of(deferral, facts.byTerm(), facts.narrator())
                        .filter(fact -> !fact.mismatches(plain, facts.targetLanguage()))
                        .isPresent())
                .map(Deferral::id)
                .toList();
        log.debug("Proofread rewrite settles segmentId={} deferrals={}", segmentId, ids.size());
        return ids;
    }

    // A segment whose earlier rewrite was kept keeps that verdict's text and status before it, so Undo proofread still
    // returns to the text no proofread touched: a later window that changes nothing (or is refused) leaves it KEPT
    // under
    // the new fingerprint, and a second kept rewrite keeps the first text before.
    private static SegmentRecord carried(final SegmentRecord before, final SegmentRecord decided) {
        final @Nullable ProofreadVerdict earlier = before.proofread();
        final ProofreadVerdict now = Objects.requireNonNull(decided.proofread(), "proofread");
        if (earlier == null
                || earlier.outcome() != ProofreadOutcome.KEPT
                || now.outcome() == ProofreadOutcome.PROPOSED) {
            return decided;
        }
        log.debug(
                "Proofread verdict of segmentId={} carries the earlier kept rewrite; outcome now={}",
                before.segmentId(),
                now.outcome());
        final String reason = now.outcome() == ProofreadOutcome.KEPT ? now.reason() : earlier.reason();
        return decided.withProofread(new ProofreadVerdict(
                now.fingerprint(),
                ProofreadOutcome.KEPT,
                reason,
                earlier.before(),
                earlier.statusBefore(),
                earlier.maskedBefore(),
                earlier.findingsBefore()));
    }

    // A verdict that kept no text keeps no text or status before it.
    private static ProofreadVerdict verdict(
            final String fingerprint, final ProofreadOutcome outcome, final String reason) {
        return new ProofreadVerdict(fingerprint, outcome, reason);
    }
}
