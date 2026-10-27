/**
 * Storage behind the {@code :api} repository and checkpoint ports: in-memory adapters ({@code .memory}, ADR-0034) for
 * the command line and the tests, SQLite adapters ({@code .sqlite}: JDBI DAOs, JSON documents, the typed settings KV
 * store; ADR-0043) for the window, chosen by {@code InMemoryPersistenceModule} or {@code SqlitePersistenceModule}. FX-free.
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
    exports ua.bookloom.persistence.sqlite;

    opens ua.bookloom.persistence to
            com.google.guice;
    opens ua.bookloom.persistence.memory to
            com.google.guice;
    // Jackson reads the stored glossary and lexicon documents through their public canonical constructors.
    opens ua.bookloom.persistence.sqlite to
            com.fasterxml.jackson.databind,
            com.google.guice;
}
