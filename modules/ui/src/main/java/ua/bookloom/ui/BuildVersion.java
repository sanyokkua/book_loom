package ua.bookloom.ui;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code String} holding the build version the About dialog reports.
 *
 * <p>A qualifier rather than a call to the version reader because {@code :ui} cannot see {@code :app}, which owns the
 * reader; the composition root binds the one value it read, so the dialog and the startup log line can never
 * disagree.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface BuildVersion {}
