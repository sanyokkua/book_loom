/**
 * The project record itself, the Book Brief and its policy enums, the stored segment decision, per-status counts,
 * QA findings, the context snapshot a retry replays, the human-readable segment locator, and the glossary,
 * translation-memory, lexicon (recurring terms), summary, deferral, run and chunk-commit records — the shapes every storage port, the review
 * desk, export and the translation job read and write.
 *
 * <p>Framework-free by rule — no Guice, Jackson, JavaFX, JDBI or parser library may appear here; this package holds
 * only records and enums (ADR-0034).
 */
@NullMarked
package ua.bookloom.api.project;

import org.jspecify.annotations.NullMarked;
