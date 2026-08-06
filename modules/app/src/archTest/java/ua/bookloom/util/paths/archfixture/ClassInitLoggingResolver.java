package ua.bookloom.util.paths.archfixture;

import org.slf4j.LoggerFactory;

/**
 * Violation fixture for {@code bootstrap-no-static-logger}, class-init half: no static logger FIELD, but the
 * static field initialiser calls {@code LoggerFactory} and so runs inside {@code <clinit>}. This pins the logging
 * context exactly as hard as a static field while being invisible to a rule that only inspects field types —
 * which is why the rule checks both.
 */
public final class ClassInitLoggingResolver {

    private static final String LOGGER_NAME =
            LoggerFactory.getLogger(ClassInitLoggingResolver.class).getName();

    public String loggerName() {
        return LOGGER_NAME;
    }
}
