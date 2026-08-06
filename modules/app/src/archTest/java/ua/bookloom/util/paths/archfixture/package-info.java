/**
 * Fixtures proving {@code bootstrap-no-static-logger} rejects both a static SLF4J field and a class-init SLF4J call.
 *
 * <p>Fixture scaffolding for the boundary suite — see {@code ua.bookloom.archtest.RuleViolationFixtureTest}. These
 * types exist only in the {@code archTest} source set and are excluded from the production import by
 * {@code ProductionClasses}, so they never reach a production classpath.
 */
@NullMarked
package ua.bookloom.util.paths.archfixture;

import org.jspecify.annotations.NullMarked;
