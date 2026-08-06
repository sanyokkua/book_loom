/**
 * The concrete DAO target {@code ports-not-concretes} forbids a caller in another module from holding.
 *
 * <p>Fixture scaffolding for the boundary suite — see {@code ua.bookloom.archtest.RuleViolationFixtureTest}. These
 * types exist only in the {@code archTest} source set and are excluded from the production import by
 * {@code ProductionClasses}, so they never reach a production classpath.
 */
@NullMarked
package ua.bookloom.persistence.archfixture;

import org.jspecify.annotations.NullMarked;
