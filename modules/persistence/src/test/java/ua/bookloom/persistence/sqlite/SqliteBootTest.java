package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** Proves the SQLite, JDBI and Jackson-time libraries resolve on the module path and work together. */
class SqliteBootTest {

    @TempDir
    Path dir;

    private SQLiteDataSource dataSource() {
        final SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("boot.db"));
        return ds;
    }

    @Test
    void selectOne_tempFileDatabase_returnsOne() throws Exception {
        try (Connection c = dataSource().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("select 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void jdbiHandle_sqliteDataSource_runsQuery() {
        final int value = Jdbi.create(dataSource())
                .withHandle(
                        h -> h.createQuery("select 41 + 1").mapTo(Integer.class).one());
        assertThat(value).isEqualTo(42);
    }

    @Test
    void javaTimeModule_instant_writesIsoString() throws Exception {
        final ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertThat(mapper.writeValueAsString(Instant.parse("2026-10-20T10:15:30Z")))
                .isEqualTo("\"2026-10-20T10:15:30Z\"");
    }
}
