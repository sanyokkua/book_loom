package ua.bookloom.app.bootstrap.archfixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Violation fixture for the {@code :app} half of {@code bootstrap-no-static-logger}'s scope.
 *
 * <p>What selects it is its <strong>package</strong>, not its name. It sits under
 * {@code ua.bookloom.app.bootstrap..} because that is how the rule identifies the pre-logging startup path — the
 * rule cannot cover the whole of {@code ua.bookloom.app}, since everything outside the bootstrap package runs after
 * logging is configured and may log freely.
 *
 * <p>It was previously selected by a name-matching predicate, and moving with that predicate's removal is the
 * point: had it stayed in {@code ua.bookloom.app.archfixture}, the rule would no longer see it and
 * {@link ua.bookloom.archtest.RuleViolationFixtureTest} would fail asserting a rejection that never came — the
 * loudest available signal that the re-scope had been only half applied.
 */
public final class Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

    public String loggerName() {
        return LOG.getName();
    }
}
