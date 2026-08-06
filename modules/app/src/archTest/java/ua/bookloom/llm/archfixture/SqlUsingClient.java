package ua.bookloom.llm.archfixture;

import java.sql.Connection;

/**
 * Violation fixture for {@code no-sql-in-core-except-persistence}: a {@code :llm} class holding a JDBC type. The
 * storage engine is an implementation detail behind the repository ports, and a caller that reaches for a
 * {@code Connection} has bypassed the seam that makes storage testable.
 */
public final class SqlUsingClient {

    public String describe(Connection connection) {
        return connection.getClass().getName();
    }
}
