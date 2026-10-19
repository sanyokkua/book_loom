/**
 * The review desk: the port the review panel calls, and its parts — the actions that move a stored segment through
 * its status machine, the retry that drafts one segment again with the context it first saw, the queue and segment
 * reads the review panel and the Export screen use, and the one counting rule the review counts and the export report
 * share.
 */
@NullMarked
package ua.bookloom.pipeline.review;

import org.jspecify.annotations.NullMarked;
