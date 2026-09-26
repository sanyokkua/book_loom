package ua.bookloom.ui;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the virtual-thread {@link java.util.concurrent.ExecutorService} for blocking I/O fan-out.
 *
 * <p>Converted together with {@link BackgroundExecutor}: leaving one executor on a string name while its twin moved
 * to a type would leave two ways of asking for a pool, and only one of them checked by the compiler. It is not for
 * inference, which is single-flight and must never be fanned out.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface IoExecutor {}
