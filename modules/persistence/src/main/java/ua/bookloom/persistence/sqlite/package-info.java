/**
 * The SQLite storage engine behind the window's repository adapters (ADR-0043): per-connection pragmas, the one
 * pre-release schema file, the pre-release rule that sets an unusable file aside, a single writer, and the
 * {@code Sqlite*} adapters of the project, segment, term-list, memory, summary, deferral, run and checkpoint ports.
 * FX-free.
 */
@NullMarked
package ua.bookloom.persistence.sqlite;

import org.jspecify.annotations.NullMarked;
