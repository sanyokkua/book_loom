package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

class WriteExecutorTest {

    @TempDir
    Path dir;

    private SqliteDatabase open() {
        return Objects.requireNonNull(SqliteDatabase.open(dir.resolve("bookloom.db"), Clock.systemUTC())
                .data());
    }

    @Test
    void write_twoConcurrentWrites_runOneAfterTheOther() throws InterruptedException {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch done = new CountDownLatch(2);
        try (SqliteDatabase db = open()) {
            for (final String name : List.of("a", "b")) {
                Thread.ofVirtual().start(() -> {
                    db.write("w", h -> {
                        events.add(name + "-start");
                        sleep();
                        events.add(name + "-end");
                        return Boolean.TRUE;
                    });
                    done.countDown();
                });
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(events.get(0).charAt(0)).isEqualTo(events.get(1).charAt(0));
        assertThat(events.get(2).charAt(0)).isEqualTo(events.get(3).charAt(0));
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void write_failingWork_rollsBackEarlierStatementsAndReturnsInternalError() {
        try (SqliteDatabase db = open()) {
            final Result<Boolean> failed = db.write("two inserts", h -> {
                h.execute("insert into settings(key, value, type) values ('k', 'v', 'STRING')");
                throw new IllegalArgumentException("boom");
            });
            assertThat(failed.isErr()).isTrue();
            assertThat(Objects.requireNonNull(failed.error()).code()).isEqualTo(ErrorCode.internal);
            assertThat(Objects.requireNonNull(failed.error()).details()).isEqualTo("storage two inserts");
            final int rows = Objects.requireNonNull(db.read(
                            "count",
                            h -> h.createQuery("select count(*) from settings")
                                    .mapTo(Integer.class)
                                    .one())
                    .data());
            assertThat(rows).isZero();
        }
    }
}
