/**
 * The acceptance rule, the per-chunk quality loop, and the self-heal calls that repair one failing segment.
 *
 * <p>{@link ua.bookloom.pipeline.heal.AcceptanceRule} decides whether a drafted or repaired target may be
 * accepted — hard gates, no soft check failed outright, confidence at τ, and, when the judge ran, its score and
 * findings. {@link ua.bookloom.pipeline.heal.QualityLoop} starts one chunk's loop: it evaluates every drafted
 * outcome, makes the chunk's one judge call when the dial enables it, and hands both to a
 * {@link ua.bookloom.pipeline.heal.ChunkDecider}, which decides one segment per {@code nextDecision()} call — a
 * {@link ua.bookloom.pipeline.heal.DraftOutcome.FlaggedAtOnce} outcome at once, a
 * {@link ua.bookloom.pipeline.heal.DraftOutcome.Drafted} one through the acceptance rule and, when it fails, up to
 * the dial's repair-round budget.
 *
 * <p>A round's self-heal call is one of: a directed fix that names concrete findings, a reflect critique and the
 * improve rewrite that consumes it for a vague concern with none, or the optional polish for a near miss — each
 * sending exactly one call through {@link ua.bookloom.pipeline.prompt.ModelCalls} and reading its reply into a
 * {@link ua.bookloom.pipeline.heal.RepairReply}.
 */
@NullMarked
package ua.bookloom.pipeline.heal;

import org.jspecify.annotations.NullMarked;
