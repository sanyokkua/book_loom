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
 * {@link ua.bookloom.pipeline.heal.DraftOutcome.Drafted} one through the reviewer's verified edits when the checks
 * pass, then the one repair when a BLOCK or REPAIR finding is left, then the acceptance rule. {@link ua.bookloom.pipeline.heal.ReuseCheck} decides,
 * before any draft, whether a context-matched memory target may be reused — the acceptance rule with no reviewer.
 *
 * <p>The one repair ({@code OneRepair}) is a segment's only content-changing call: its draft prompt with the previous
 * attempt and every defect named, sent through a {@link ua.bookloom.pipeline.heal.RepairCall} and read into a
 * {@link ua.bookloom.pipeline.heal.RepairReply}; it stays only when the never-worse gate keeps it. The reviewer's fix of
 * a refused edit is a directed fix of its own until the reviewer is retired.
 */
@NullMarked
package ua.bookloom.pipeline.heal;

import org.jspecify.annotations.NullMarked;
