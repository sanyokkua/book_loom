package ua.bookloom.util.paths;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Reads an environment variable or a system property by name, answering null when it is not set. A named type, so a
 * lookup that may come back empty is not mistaken for a plain string-to-string function.
 */
@FunctionalInterface
public interface NameLookup extends Function<String, @Nullable String> {}
