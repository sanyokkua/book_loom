/**
 * Storage ports for a project's persisted state — the project itself, its segments, glossary, recurring-term lexicon, translation memory,
 * rolling summary, deferrals, run history and the checkpoint that commits a chunk's decided segments together
 * (ADR-0034).
 *
 * <p>Interfaces only, framework-free: review, export, retry and resume read one source of truth here so a later
 * SQLite implementation can replace the in-memory adapter without any caller changing. Every method returns
 * {@code Result} — no exception crosses this boundary.
 */
@NullMarked
package ua.bookloom.api.persistence;

import org.jspecify.annotations.NullMarked;
