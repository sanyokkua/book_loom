/**
 * Persistence facade: DB bootstrap, connection and WAL configuration.
 *
 * <p>The only module permitted to import {@code java.sql}, JDBI or Flyway. It does not own the process
 * single-instance lock — that is {@code :app} plus {@code ua.bookloom.util.paths}.
 */
@NullMarked
package ua.bookloom.persistence;

import org.jspecify.annotations.NullMarked;
