/**
 * The final audit: after a run the cheap deterministic checks look again at every accepted segment, so a defect that
 * slipped through while the run went on is listed as "suspicious" with the check that fired, instead of passing unseen.
 */
@NullMarked
package ua.bookloom.pipeline.audit;

import org.jspecify.annotations.NullMarked;
