package ua.bookloom.util.log;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The contract between the code that decides a segment is worth keeping evidence for and the evidence log that keeps
 * it, so neither depends on the other.
 *
 * <p>A log call carrying a marker of this name, made with the MDC key {@code segment} set, tells the evidence appender
 * to write out the TRACE lines it has been holding for that segment and to pass the segment's later lines straight
 * through. A segment that never gets the marker is forgotten as newer ones arrive.
 */
// Checkstyle parses source text before Lombok's annotation processor runs, so it cannot see the private constructor
// @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvidenceLog {

    /** The name of the SLF4J marker that keeps a segment's evidence. */
    public static final String KEEP_MARKER = "EVIDENCE_KEEP";

    /** The MDC key naming the segment a line is about. */
    public static final String SEGMENT_KEY = "segment";
}
