/**
 * Fixture proving {@code bootstrap-no-static-logger} covers the {@code :app} half of the pre-logging startup path.
 *
 * <p>This package sits under {@code ua.bookloom.app.bootstrap} because that package <em>is</em> the rule's scope:
 * membership is what declares a class to run before Logback is configured. A fixture placed anywhere else would be
 * invisible to the rule it exists to trip.
 *
 * <p>Fixture scaffolding for the boundary suite — see {@code ua.bookloom.archtest.RuleViolationFixtureTest}. These
 * types exist only in the {@code archTest} source set and are excluded from the production import by
 * {@code ProductionClasses}, so they never reach a production classpath.
 */
@NullMarked
package ua.bookloom.app.bootstrap.archfixture;

import org.jspecify.annotations.NullMarked;
