package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
            startSlowWrite(db, "a", events, done);
            startSlowWrite(db, "b", events, done);
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(events.get(0).charAt(0)).isEqualTo(events.get(1).charAt(0));
        assertThat(events.get(2).charAt(0)).isEqualTo(events.get(3).charAt(0));
    }

    private static void startSlowWrite(
            final SqliteDatabase db, final String name, final List<String> events, final CountDownLatch done) {
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

    @Test
    void write_callerInterruptedWhileWaiting_answersTheCommittedWriteAndKeepsTheInterrupt()
            throws InterruptedException {
        final AtomicReference<Result<Integer>> answer = new AtomicReference<>();
        final AtomicBoolean stillInterrupted = new AtomicBoolean();
        try (SqliteDatabase db = open()) {
            final Thread caller = Thread.ofVirtual().start(() -> {
                Thread.currentThread().interrupt();
                answer.set(db.write(
                        "insert",
                        h -> h.execute("insert into settings(key, value, type) values ('k', 'v', 'STRING')")));
                stillInterrupted.set(Thread.currentThread().isInterrupted());
            });
            caller.join();

            assertThat(answer.get()).isNotNull().extracting(Result::data).isEqualTo(1);
            assertThat(stillInterrupted).isTrue();
            assertThat(settingsRows(db)).isEqualTo(1);
        }
    }

    @Test
    void write_nestedInsideAnotherWrite_answersInternalErrorInsteadOfWaitingForever() {
        try (SqliteDatabase db = open()) {
            final Result<Result<Integer>> outer = assertTimeoutPreemptively(
                    Duration.ofSeconds(10), () -> db.write("outer", h -> db.write("inner", inner -> 1)));

            final Result<Integer> inner = Objects.requireNonNull(outer.data());
            assertThat(inner.isErr()).isTrue();
            assertThat(Objects.requireNonNull(inner.error()).code()).isEqualTo(ErrorCode.internal);
            assertThat(Objects.requireNonNull(inner.error()).details()).isEqualTo("storage inner");
        }
    }

    private static int settingsRows(final SqliteDatabase db) {
        return Objects.requireNonNull(db.read(
                        "count",
                        h -> h.createQuery("select count(*) from settings")
                                .mapTo(Integer.class)
                                .one())
                .data());
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
            assertThat(settingsRows(db)).isZero();
        }
    }
}
