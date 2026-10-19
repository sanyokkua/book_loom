/**
 * The reviewer that fixes in place: one call per batch of drafted candidates, answered per segment as {@code ok}, a
 * list of find-and-replace edits, or a rewrite, and the code that verifies every edit instead of asking the model again
 * ({@link ua.bookloom.pipeline.reviewer.EditVerifier}, {@link ua.bookloom.pipeline.reviewer.EditApplier}).
 */
@NullMarked
package ua.bookloom.pipeline.reviewer;

import org.jspecify.annotations.NullMarked;
