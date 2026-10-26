package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.ui.DiagnosticLog;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.SettingWriter;

/** The detailed-log switch is a marker file in the data folder, written and removed off the FX thread. */
class DetailedLogSwitchTest {

    @TempDir
    private Path dir;

    private DetailedLogSwitch over(final boolean detailed, final @Nullable String decidedBy) {
        return overLog(detailed, decidedBy, detailed && decidedBy == null);
    }

    private DetailedLogSwitch overLog(
            final boolean detailed, final @Nullable String decidedBy, final boolean developmentDefault) {
        return new DetailedLogSwitch(
                new DiagnosticLog(
                        dir.resolve("logs"), detailed, dir.resolve("detailed-log.on"), decidedBy, developmentDefault),
                new SettingWriter(new MemorySettings(), Runnable::run, Runnable::run));
    }

    @Test
    void choose_on_createsMarker() {
        final DetailedLogSwitch detailed = over(false, null);

        detailed.choose(true);

        assertThat(dir.resolve("detailed-log.on")).exists();
        assertThat(detailed.onProperty().get()).isTrue();
        assertThat(detailed.saveFailedProperty().get()).isFalse();
    }

    @Test
    void choose_on_twice_stillOneMarkerAndNoFailure() {
        final DetailedLogSwitch detailed = over(false, null);

        detailed.choose(true);
        detailed.choose(true);

        assertThat(dir.resolve("detailed-log.on")).exists();
        assertThat(detailed.saveFailedProperty().get()).isFalse();
    }

    @Test
    void choose_off_deletesMarker() throws IOException {
        Files.writeString(dir.resolve("detailed-log.on"), "x");
        final DetailedLogSwitch detailed = over(false, null);

        detailed.choose(false);

        assertThat(dir.resolve("detailed-log.on")).doesNotExist();
        assertThat(detailed.onProperty().get()).isFalse();
    }

    @Test
    void choose_offWithNoMarker_noFailure() {
        final DetailedLogSwitch detailed = over(false, null);

        detailed.choose(false);

        assertThat(detailed.saveFailedProperty().get()).isFalse();
    }

    @Test
    void load_markerPresent_on() throws IOException {
        Files.writeString(dir.resolve("detailed-log.on"), "x");
        final DetailedLogSwitch detailed = over(false, null);

        detailed.load();

        assertThat(detailed.onProperty().get()).isTrue();
        assertThat(detailed.loadedProperty().get()).isTrue();
    }

    @Test
    void load_noMarker_off() {
        final DetailedLogSwitch detailed = over(false, null);

        detailed.load();

        assertThat(detailed.onProperty().get()).isFalse();
        assertThat(detailed.loadedProperty().get()).isTrue();
    }

    @Test
    void choose_folderMissing_saveFailed() {
        final DetailedLogSwitch detailed = new DetailedLogSwitch(
                new DiagnosticLog(
                        dir.resolve("logs"), false, dir.resolve("missing").resolve("detailed-log.on"), null, false),
                new SettingWriter(new MemorySettings(), Runnable::run, Runnable::run));

        detailed.choose(true);

        assertThat(detailed.saveFailedProperty().get()).isTrue();
        assertThat(detailed.onProperty().get()).isTrue();
    }

    @Test
    void developmentAlways_detailedNoMarkerNoOverride_true() {
        final DetailedLogSwitch detailed = over(true, null);
        detailed.load();

        assertThat(detailed.developmentAlways()).isTrue();
        assertThat(detailed.runningNow()).isTrue();
    }

    @Test
    void developmentAlways_overrideDecided_false() {
        final DetailedLogSwitch detailed = over(true, "BOOKLOOM_TRACE_FILE");
        detailed.load();

        assertThat(detailed.developmentAlways()).isFalse();
        assertThat(detailed.decidedBy()).isEqualTo("BOOKLOOM_TRACE_FILE");
    }

    // IF an installed app that the marker switched on were taken for a development run, THEN the tab would say the
    // switch is ignored while turning it off does work.
    @Test
    void developmentAlways_markerDecidedInstalled_false() {
        final DetailedLogSwitch detailed = overLog(true, null, false);
        detailed.load();

        assertThat(detailed.developmentAlways()).isFalse();
    }

    @Test
    void developmentAlways_developmentDefaultSwitchOff_true() {
        final DetailedLogSwitch detailed = overLog(true, null, true);
        detailed.load();

        assertThat(detailed.developmentAlways()).isTrue();
    }

    @Test
    void developmentAlways_developmentDefaultSwitchOn_false() throws IOException {
        Files.writeString(dir.resolve("detailed-log.on"), "x");
        final DetailedLogSwitch detailed = overLog(true, null, true);
        detailed.load();

        assertThat(detailed.developmentAlways()).isFalse();
    }

    /** Holds tasks until the test runs them, newest first, like a pool that finishes tasks out of order. */
    private static final class OutOfOrder implements Executor {
        private final Deque<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(final Runnable task) {
            pending.push(task);
        }

        void drain() {
            while (!pending.isEmpty()) {
                pending.pop().run();
            }
        }
    }

    // IF two quick choices could be written in either order, THEN the marker could end up opposite to the switch.
    @Test
    void choose_onThenOffWithOutOfOrderPool_markerFollowsLastChoice() {
        final OutOfOrder pool = new OutOfOrder();
        final DetailedLogSwitch detailed = new DetailedLogSwitch(
                new DiagnosticLog(dir.resolve("logs"), false, dir.resolve("detailed-log.on"), null, false),
                new SettingWriter(new MemorySettings(), pool, Runnable::run));

        detailed.choose(true);
        detailed.choose(false);
        pool.drain();

        assertThat(dir.resolve("detailed-log.on")).doesNotExist();
        assertThat(detailed.onProperty().get()).isFalse();
    }

    // IF a read that began before the person's choice landed after it, THEN it would undo the choice on screen.
    @Test
    void load_landsAfterChoose_choiceStays() throws IOException {
        Files.writeString(dir.resolve("detailed-log.on"), "x");
        final OutOfOrder fx = new OutOfOrder();
        final DetailedLogSwitch detailed = new DetailedLogSwitch(
                new DiagnosticLog(dir.resolve("logs"), false, dir.resolve("detailed-log.on"), null, false),
                new SettingWriter(new MemorySettings(), Runnable::run, fx));

        detailed.load();
        detailed.choose(false);
        fx.drain();

        assertThat(detailed.onProperty().get()).isFalse();
        assertThat(dir.resolve("detailed-log.on")).doesNotExist();
    }
}
