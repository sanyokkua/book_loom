/**
 * App-paths resolver: per-OS data/log/config directories, first-run creation, and the single-instance lock-file
 * path. Runs pre-injector, before logging and before SQLite (DD-39, ADR-0015). Also holds the output-file naming
 * rule shared by the command line and the UI, which needs no logging or injection.
 */
@NullMarked
package ua.bookloom.util.paths;

import org.jspecify.annotations.NullMarked;
