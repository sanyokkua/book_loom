/**
 * Everything that runs <strong>before logging exists</strong>: path resolution, directory creation, the
 * single-instance lock, the pre-logging failure dialog, and the programmatic Logback configuration itself.
 *
 * <p>Membership of this package is a declaration, not a filing decision. The ArchUnit rule
 * {@code bootstrap-no-static-logger} scopes to {@code ua.bookloom.app.bootstrap..}, so a class placed here may not
 * declare a static SLF4J {@code Logger} or touch {@code org.slf4j} from a static initializer — and a class that
 * runs before logging but is placed <em>outside</em> this package is not protected at all. That is why the rule
 * identifies the startup path by package rather than by class name: a name-matching predicate silently missed the
 * one class most able to make the mistake, the one whose entire job is to configure Logback.
 *
 * <p>The failure it prevents is quiet rather than loud. A logger created before {@link
 * ua.bookloom.app.bootstrap.LoggingBootstrap} runs pins Logback's file appender to a default directory; the
 * application then works perfectly, every test passes, and the defect surfaces as a user who cannot find their log
 * file (DD-39, ADR-0015).
 */
@NullMarked
package ua.bookloom.app.bootstrap;

import org.jspecify.annotations.NullMarked;
