package ua.bookloom.util.paths.archfixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Violation fixture for {@code bootstrap-no-static-logger}, static-field half: a paths-resolver class holding a
 * static SLF4J logger. It would initialise before the resolved log directory is published, pinning Logback to a
 * default location that is not the one the app then uses (DD-39, ADR-0015).
 */
public final class StaticLoggerResolver {

    private static final Logger LOG = LoggerFactory.getLogger(StaticLoggerResolver.class);

    public String loggerName() {
        return LOG.getName();
    }
}
