/**
 * The typed registry of the preferences the application keeps between launches — each key with its type, default and
 * the pure-Java text codec a store uses to hold it (D12 of {@code finalize-ui-and-persistence}).
 *
 * <p>Framework-free: a store sees only the key and the text, so the in-memory and the SQLite adapter share one
 * meaning for every value.
 */
@NullMarked
package ua.bookloom.api.settings;

import org.jspecify.annotations.NullMarked;
