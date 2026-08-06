/**
 * Launcher, the JavaFX {@code Application} subclass, the Guice composition root and two-phase init.
 *
 * <p>On the pre-logging startup path: the single-instance lock is acquired here before the injector is built and
 * before SQLite opens, so no class in this package may hold a static SLF4J logger (DD-39, ADR-0015).
 */
@NullMarked
package ua.bookloom.app;

import org.jspecify.annotations.NullMarked;
