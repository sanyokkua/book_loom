/**
 * The package-private command-line adapter for one-book translation.
 *
 * <p>The package is opened to Guice from {@code module-info.java} but is not exported: the public entry point is
 * {@link ua.bookloom.app.bootstrap.TranslateLauncher}, while argument parsing and console reporting remain an
 * implementation detail of the application module.
 */
@org.jspecify.annotations.NullMarked
package ua.bookloom.app.cli;
