/**
 * Storage behind the {@code :api} repository and checkpoint ports. Today these are in-memory adapters
 * ({@code ua.bookloom.persistence.memory}, ADR-0034), so nothing survives a restart; the SQLite adapter (JDBI DAOs,
 * JSON documents, the typed settings KV store; ADR-0043) replaces them for the window behind the same ports. FX-free.
 *
 * <p>The only module permitted to import {@code java.sql}. Note it does NOT own the process single-instance lock —
 * that is acquired pre-injector by {@code :app} using the path resolved by {@code ua.bookloom.util.paths}
 * (docs/implementation_plan/01_MODULE_INVENTORY.md#lock-ownership).
 */
module ua.bookloom.persistence {
    // Compile-time only: JSpecify supplies the `@NullMarked` package markers that switch
    // NullAway on. Nothing reflects on them at runtime (02_QUALITY_GATES.md#null-safety).
    requires static org.jspecify;
    // Compile-time only: Lombok desugars `@RequiredArgsConstructor`/`@Slf4j`/`@Builder` on SERVICE
    // classes into ordinary members and leaves nothing behind at runtime (DD-05, ADR-0014). Data
    // carriers stay records and never use it.
    requires static lombok;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires java.sql;
    requires com.google.guice;
    requires org.slf4j;
    requires org.xerial.sqlitejdbc;
    requires org.jdbi.v3.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    exports ua.bookloom.persistence;

    opens ua.bookloom.persistence to
            com.google.guice;
    opens ua.bookloom.persistence.memory to
            com.google.guice;
}
