/**
 * The composition root: {@code Launcher}, the {@code Application} subclass, the Guice injector, two-phase init and
 * the process single-instance lock acquired before the injector and before SQLite opens.
 *
 * <p>The only module that sees every other one — assembly happens in exactly one place.
 */
module ua.bookloom.app {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on SERVICE
    // classes into ordinary members and leaves nothing behind at runtime (DD-05, ADR-0014). Data
    // carriers stay records and never use it.
    requires static lombok;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires ua.bookloom.document;
    requires ua.bookloom.llm;
    requires ua.bookloom.persistence;
    requires ua.bookloom.pipeline;
    requires ua.bookloom.ui;
    requires javafx.controls;
    requires javafx.graphics;
    requires com.google.guice;
    requires org.slf4j;
    // The Logback binding, required rather than merely on the runtime path because logging is configured
    // PROGRAMMATICALLY: `LoggerContext` and `RollingFileAppender` are compiled against. Confined to one class in
    // `ua.bookloom.app.bootstrap` — no other module requires it, and `api-is-framework-free` bans it from `:api`
    // outright. Note that `requires` satisfying javac says nothing about the SLF4J provider actually resolving at
    // runtime, which is why the boot smoke asserts the startup line was really emitted (a no-op logger emits none).
    requires ch.qos.logback.classic;
    // `logback-core` is a separate JPMS module and `logback-classic` does not re-export it, so the appender and
    // rolling-policy types (`RollingFileAppender`, `SizeAndTimeBasedRollingPolicy`, `FileSize`) are not readable
    // without naming it here. Compiling against the classic module alone fails outright — which is the module
    // system doing its job, since these really are two distinct dependencies.
    requires ch.qos.logback.core;

    exports ua.bookloom.app;

    opens ua.bookloom.app to
            com.google.guice;
    opens ua.bookloom.app.cli to
            com.google.guice;
}
