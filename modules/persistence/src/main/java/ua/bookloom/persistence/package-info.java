/**
 * The persistence module's Guice wiring, binding each {@code :api} storage port to its in-memory adapter
 * (ADR-0034); the SQLite bootstrap, connection and WAL configuration land here with the SQLite adapter.
 *
 * <p>The only module permitted to import {@code java.sql}, JDBI or Flyway. It does not own the process
 * single-instance lock — that is {@code :app} plus {@code ua.bookloom.util.paths}.
 */
@NullMarked
package ua.bookloom.persistence;

import org.jspecify.annotations.NullMarked;
