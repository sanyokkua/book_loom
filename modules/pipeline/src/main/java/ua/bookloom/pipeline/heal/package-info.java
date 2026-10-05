/**
 * The acceptance rule, the per-chunk quality loop, and the self-heal calls that repair one failing segment.
 *
 * <p>{@link ua.bookloom.pipeline.heal.AcceptanceRule} decides whether a drafted, edited or repaired target may be
 * accepted — hard gates pass, no check blocks and no verified blocker is left; confidence only orders segments.
 * {@link ua.bookloom.pipeline.heal.QualityLoop} starts one chunk's loop: it evaluates every drafted
 * outcome, makes the chunk's reviewer pass or passes when the dial enables them, and hands both to a
 * {@link ua.bookloom.pipeline.heal.ChunkDecider}, which decides one segment per {@code nextDecision()} call — a
 * {@link ua.bookloom.pipeline.heal.DraftOutcome.FlaggedAtOnce} outcome at once, a
 * {@link ua.bookloom.pipeline.heal.DraftOutcome.Reused} one as accepted from memory with no call, a
 * {@link ua.bookloom.pipeline.heal.DraftOutcome.Drafted} one through the acceptance rule and, when it fails, up to
 * the dial's repair-round budget, or, when the checks pass, through the reviewer's verified edits. {@link ua.bookloom.pipeline.heal.ReuseCheck} decides,
 * before any draft, whether a context-matched memory target may be reused — the acceptance rule with no reviewer.
 *
 * <p>A repair round's one call is a directed fix that names the evidenced findings — a failed check, or a reviewer
 * quote the app found and no edit fixed — sent through {@link ua.bookloom.pipeline.prompt.ModelCalls} and read into a
 * {@link ua.bookloom.pipeline.heal.RepairReply}. The path carries a best candidate, discards a step that does not
 * lower the blocker set and stops when a round makes no progress; there is no call without a finding to name.
 */
@NullMarked
package ua.bookloom.pipeline.heal;

import org.jspecify.annotations.NullMarked;
