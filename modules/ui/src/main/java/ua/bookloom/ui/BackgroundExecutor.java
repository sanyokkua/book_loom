package ua.bookloom.ui;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the daemon {@link java.util.concurrent.ExecutorService} that runs bounded, cancellable application work off
 * the FX Application Thread.
 *
 * <p>A qualifier declared here rather than the bare name {@code "background"} because {@code :ui} cannot see
 * {@code :app}, which owns the pool: with a name, the binding key would cross the module edge as a string literal
 * that nothing checks, and a typo would surface only when the first run is started. The composition root binds the
 * pool under this annotation and {@code UiModule} requires it, so a missing binding fails when the injector is built.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface BackgroundExecutor {}
