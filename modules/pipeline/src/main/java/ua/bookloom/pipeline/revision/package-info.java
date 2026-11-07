/**
 * Backward revision: the deferrals a run or a glossary edit leaves behind for segments already decided, the code
 * sweep of renamed glossary targets that the Proofread stage of every run calls
 * ({@link ua.bookloom.pipeline.revision.CodeSweep}), and the consistency pass that answers the deferrals before an export — the same sweep, a revision call for a
 * character whose gender became known, and a proposal instead of a change wherever the person edited the segment.
 * Group 14 moves the survivors to {@code pipeline.proofread} and deletes the rest.
 */
@NullMarked
package ua.bookloom.pipeline.revision;

import org.jspecify.annotations.NullMarked;
