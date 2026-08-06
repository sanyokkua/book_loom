/**
 * App-paths resolver: per-OS data/log/config directories, first-run creation, and the single-instance lock-file
 * path. Runs pre-injector, before logging and before SQLite (DD-39, ADR-0015).
 */
@NullMarked
package ua.bookloom.util.paths;

import org.jspecify.annotations.NullMarked;
