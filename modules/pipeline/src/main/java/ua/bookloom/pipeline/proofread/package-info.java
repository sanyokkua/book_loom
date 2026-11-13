/**
 * The Proofread stage, the last stage of every run (design D15): by code, at every dial, the renamed glossary targets
 * are swept through the decided text, the narrator's gender is inferred, the deferrals whose fact has arrived are
 * settled, and the mark list names the segments worth a second look with their reasons. A mark is a finding or an open
 * deferral on a segment, never a record of its own, so the list is computed from what is stored.
 */
@NullMarked
package ua.bookloom.pipeline.proofread;

import org.jspecify.annotations.NullMarked;
