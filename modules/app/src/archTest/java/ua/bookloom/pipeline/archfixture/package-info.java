/**
 * Fixture proving {@code no-http-in-core-except-llm} rejects an HTTP client outside {@code :llm}.
 *
 * <p>Fixture scaffolding for the boundary suite — see {@code ua.bookloom.archtest.RuleViolationFixtureTest}. These
 * types exist only in the {@code archTest} source set and are excluded from the production import by
 * {@code ProductionClasses}, so they never reach a production classpath.
 */
@NullMarked
package ua.bookloom.pipeline.archfixture;

import org.jspecify.annotations.NullMarked;
