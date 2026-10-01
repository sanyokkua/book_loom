package ua.bookloom.ui;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.framework.junit5.ApplicationFixture;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX's {@code ApplicationTest} lifecycle — {@link #init()} and {@link #start(Stage)} on the shared primary stage
 * before each test, {@link #stop()}, every window hidden and the robot's input released after it — and
 * {@link #interact(Runnable)}, without their fixed sleeps.
 *
 * <p>{@code ApplicationTest} ends each of its four setup and cleanup steps with {@code waitForFxEvents()}: five round
 * trips to the FX Application Thread with a 10 ms sleep after each, about a quarter of a second per test and most of
 * the {@code :ui} suite's time. What those sleeps really bought was a few pulses — the shown scene styled, laid out
 * and given its initial focus — so here each step waits for exactly that: its own work on the FX thread, the events
 * it queued, and then {@value #PULSES} real pulses of every shown scene.
 */
public abstract class FxTestBase extends FxRobot implements ApplicationFixture {

    private static final long FX_TIMEOUT_SECONDS = 10;

    // Matches the five round trips of `waitForFxEvents()`, so an action that queues follow-up work a few levels deep
    // has the same chance to finish; only the sleeps between them are dropped.
    private static final int ROUND_TRIPS = 5;

    // The first pulse after `show()` styles and lays the scene out; the scene's initial focus owner is settled by the
    // pulse after. Two pulses is what a test that clicks and types needs before its first input.
    private static final int PULSES = 2;

    @BeforeEach
    public final void startFxApplication() throws Exception {
        final Stage stage = FxToolkit.registerPrimaryStage();
        init();
        onFxThread(() -> {
            start(stage);
            return null;
        });
        settle();
    }

    @AfterEach
    public final void stopFxApplication() throws Exception {
        onFxThread(() -> {
            stop();
            return null;
        });
        onFxThread(() -> {
            List.copyOf(Window.getWindows()).forEach(Window::hide);
            return null;
        });
        drainEvents();
        // `release` waits for FX events; only a test that left a key or button down pays for it.
        if (!robotContext().getMouseRobot().getPressedButtons().isEmpty()) {
            release(new MouseButton[0]);
        }
        if (!robotContext().getKeyboardRobot().getPressedKeys().isEmpty()) {
            release(new KeyCode[0]);
        }
    }

    @Override
    public void init() throws Exception {
        // nothing to prepare off the FX thread unless a test overrides this
    }

    @Override
    public void start(final Stage stage) throws Exception {
        // a test that shows nothing overrides nothing
    }

    @Override
    public void stop() throws Exception {
        // nothing to release unless a test overrides this
    }

    /**
     * Runs the action on the FX Application Thread, then {@link #settle()}s — what {@code FxRobot.interact} does with
     * {@code waitForFxEvents()}, without the sleeps, which in the screen suites were half of all the time spent.
     */
    @Override
    public FxRobot interact(final Runnable runnable) {
        return interact(() -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <T> FxRobot interact(final Callable<T> callable) {
        try {
            onFxThread(callable);
            settle();
        } catch (final TimeoutException e) {
            throw new IllegalStateException("the FX Application Thread did not answer", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the FX Application Thread", e);
        }
        return this;
    }

    /**
     * TestFX's own {@code interact}: the action, then {@code waitForFxEvents()} with its fixed sleeps. Only for a test
     * whose input pacing is part of what it measures (a wheel spun at a hand's speed), so its timing stays what it was
     * written against.
     */
    protected final FxRobot interactAtTestFxPace(final Runnable runnable) {
        return super.interact(runnable);
    }

    /** Drains the FX events queued so far, then waits for every shown scene to go through {@value #PULSES} pulses. */
    private static void settle() throws TimeoutException, InterruptedException {
        drainEvents();
        for (int pulse = 0; pulse < PULSES; pulse++) {
            awaitPulse();
        }
    }

    private static void drainEvents() throws TimeoutException {
        for (int trip = 0; trip < ROUND_TRIPS; trip++) {
            onFxThread(() -> null);
        }
    }

    private static void awaitPulse() throws TimeoutException, InterruptedException {
        final List<Scene> scenes = onFxThread(() -> Window.getWindows().stream()
                .filter(Window::isShowing)
                .map(Window::getScene)
                .filter(scene -> scene != null)
                .toList());
        if (scenes.isEmpty()) {
            return;
        }
        final CountDownLatch pulsed = new CountDownLatch(scenes.size());
        final List<Runnable> listeners =
                scenes.stream().map(scene -> (Runnable) pulsed::countDown).toList();
        onFxThread(() -> {
            for (int i = 0; i < scenes.size(); i++) {
                scenes.get(i).addPostLayoutPulseListener(listeners.get(i));
            }
            Platform.requestNextPulse();
            return null;
        });
        final boolean done = pulsed.await(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        onFxThread(() -> {
            for (int i = 0; i < scenes.size(); i++) {
                scenes.get(i).removePostLayoutPulseListener(listeners.get(i));
            }
            return null;
        });
        if (!done) {
            throw new TimeoutException("no pulse within " + FX_TIMEOUT_SECONDS + " s");
        }
    }

    private static <T> T onFxThread(final Callable<T> action) throws TimeoutException {
        return WaitForAsyncUtils.waitFor(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS, WaitForAsyncUtils.asyncFx(action));
    }
}
