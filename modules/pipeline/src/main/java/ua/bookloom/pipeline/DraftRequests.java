package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftStep;

/**
 * The requests of a segment's draft — the first draft, its format re-ask and its one repair — built in one place, so
 * the step that sends them and a prompt eval that only needs them cannot build different ones. A request is the one
 * the step sends before the run's seam sizes it to the window.
 */
@Slf4j
public final class DraftRequests {

    private final GateFunction gate;
    private final DraftPrompts prompts;

    DraftRequests(final GateFunction gate, final DraftPrompts prompts) {
        this.gate = gate;
        this.prompts = prompts;
    }

    /**
     * The request of a segment's first draft, planned over the window and map the run shows it.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null window, map and memory the draft is shown
     * @param mask the non-null spans hidden in the segment
     * @return the draft's request
     */
    public ChatRequest draft(final Segment segment, final TranslateContext context, final ProtectedMask mask) {
        return build(DraftAttempt.of(segment, context, mask, "", false), DraftStep.DRAFT, "", "");
    }

    /**
     * The request of a review retry's first draft, replaying the stored context.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null replayed context
     * @param mask the non-null spans hidden in the segment
     * @return the draft's request
     */
    public ChatRequest draft(final Segment segment, final DraftContext context, final ProtectedMask mask) {
        return build(DraftAttempt.of(segment, context, mask), DraftStep.DRAFT, "", "");
    }

    /**
     * The request of the one format re-ask a reply that cannot be read is sent, with the reader's diagnosis of it.
     *
     * @param segment the non-null segment being drafted
     * @param context the non-null window, map and memory the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param rejectedReply the non-null raw reply the reader refused
     * @return the re-ask's request
     */
    public ChatRequest formatReask(
            final Segment segment,
            final TranslateContext context,
            final ProtectedMask mask,
            final String rejectedReply) {
        final DraftAttempt attempt = DraftAttempt.of(segment, context, mask, "", false);
        final String diagnostic = prompts.parse(rejectedReply, attempt).diagnostic();
        return build(attempt, DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic);
    }

    /**
     * The request of the one repair: the draft's plan with the previous attempt and its defects in block 11, sent with
     * the retry seed.
     *
     * @param segment the non-null segment being repaired
     * @param context the non-null window, map and memory the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param previousAttempt the non-null masked text the repair rewrites
     * @param defects the non-null findings to fix, each with its explanation and the span it quotes
     * @return the repair's request
     */
    public ChatRequest repair(
            final Segment segment,
            final TranslateContext context,
            final ProtectedMask mask,
            final String previousAttempt,
            final List<QaFinding> defects) {
        return build(
                DraftAttempt.of(segment, context, mask, "", false),
                DraftStep.REPAIR,
                previousAttempt,
                defectLines(defects));
    }

    /**
     * The request of the one repair for a target whose formatting marks the code cannot put back.
     *
     * @param segment the non-null segment being drafted
     * @param context the non-null window, map and memory the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param rejectedTarget the non-null target text the model gave, before its whitespace is restored
     * @return the repair's request naming the gate's finding, or empty when the gate or a code repair accepts it
     */
    public Optional<ChatRequest> repairOfLostMarks(
            final Segment segment,
            final TranslateContext context,
            final ProtectedMask mask,
            final String rejectedTarget) {
        final String rejected = WhitespaceRestoration.restore(segment.masked(), rejectedTarget.strip());
        if (gate.restore(segment, rejected) instanceof GateResult.GateFailed failed
                && !restoresByCode(segment, rejected)) {
            return Optional.of(repair(segment, context, mask, rejected, List.of(failed.finding())));
        }
        log.debug("No repair of lost marks needed segmentId={}", segment.id());
        return Optional.empty();
    }

    private boolean restoresByCode(final Segment segment, final String rejected) {
        return gate.restoreRepairing(segment, rejected, PlaceholderRepair.RESTORE_MISSING)
                        instanceof GateResult.Restored
                || gate.restoreRepairing(segment, rejected, PlaceholderRepair.REWRAP_ALL)
                        instanceof GateResult.Restored;
    }

    /**
     * The defects a repair names, one {@code kind (severity): explanation} line each; the explanation quotes the span.
     *
     * @param defects the non-null findings
     * @return the lines joined by newlines; empty when there is none
     */
    static String defectLines(final List<QaFinding> defects) {
        return defects.stream()
                .map(finding -> finding.kind() + " (" + finding.severity() + "): " + finding.note())
                .collect(Collectors.joining("\n"));
    }

    ChatRequest build(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        Objects.requireNonNull(attempt, "attempt");
        final ChatRequest built = prompts.build(attempt, step, rejected, diagnostic);
        final ChatRequest request = step == DraftStep.REPAIR ? built.withSeed(ChatRequests.RETRY_SEED) : built;
        log.debug(
                "Built chat request segmentId={} step={} maskedLength={} messageCount={} expectedTokens={}"
                        + " capTokens={}",
                attempt.segment().id(),
                step,
                attempt.shownText().length(),
                request.messages().size(),
                request.expectedOutputTokens() == null ? "none" : request.expectedOutputTokens(),
                request.maxOutputTokens() == null ? "none" : request.maxOutputTokens());
        return request;
    }

    /** What the shown call of a draft step names besides its request, built from the same values. */
    CallDescriptor descriptor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        return prompts.descriptor(Objects.requireNonNull(attempt, "attempt"), step, rejected, diagnostic);
    }

    /** The prompts this draft step's requests are made and read with. */
    DraftPrompts prompts() {
        return prompts;
    }
}
