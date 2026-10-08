package ua.bookloom.ui.control;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Every short transition the window plays, with its durations in one place, and the reduced-motion switch that turns
 * them all off.
 *
 * <p>Motion is never the only carrier of a change: each transition only eases a node into the state it is set to at
 * once when motion is reduced. Motion is reduced when {@code BOOKLOOM_REDUCE_MOTION} (or else the system property
 * {@code bookloom.reduceMotion}) says so — {@code 1}/{@code true}/{@code yes} on, {@code 0}/{@code false}/{@code no}
 * off — and otherwise when the operating system asks for it. The test tasks set the property, so a TestFX test reads
 * the final state at once. Everything here runs on the FX Application Thread; nothing logs per frame.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Motion {

    /** A nudge: the current navigation step settling, a screen's content appearing. */
    public static final Duration QUICK = Duration.millis(120);

    /** A surface arriving: a dialog card, a banner, a toast, the busy card. */
    public static final Duration STANDARD = Duration.millis(160);

    /** A value travelling: a progress bar moving to its new fraction. */
    public static final Duration SLOW = Duration.millis(200);

    static final String ENVIRONMENT = "BOOKLOOM_REDUCE_MOTION";
    static final String PROPERTY = "bookloom.reduceMotion";

    private static final Set<String> ON = Set.of("1", "true", "yes", "on");
    private static final Set<String> OFF = Set.of("0", "false", "no", "off");
    private static final double CARD_START_SCALE = 0.96;
    private static final double SETTLE_START_OPACITY = 0.6;
    private static final String PROGRESS_KEY = "bookloom.motion.progress";

    /**
     * Whether transitions are off now, read at each call so a switch made in the OS applies to the next one.
     *
     * @return {@code true} if motion is reduced
     */
    public static boolean isReduced() {
        return decide(System.getenv(ENVIRONMENT), System.getProperty(PROPERTY), platformReduced());
    }

    /**
     * The explicit switch wins over the system's preference; an unknown value of the switch is ignored.
     *
     * @param environment the environment variable's value, or {@code null} when unset
     * @param property the system property's value, or {@code null} when unset
     * @param platform whether the operating system asks for reduced motion
     * @return {@code true} if motion is reduced
     */
    static boolean decide(final @Nullable String environment, final @Nullable String property, final boolean platform) {
        for (final String value : new String[] {environment, property}) {
            if (value != null) {
                final String word = value.trim().toLowerCase(Locale.ROOT);
                if (ON.contains(word)) {
                    return true;
                }
                if (OFF.contains(word)) {
                    return false;
                }
            }
        }
        return platform;
    }

    /**
     * Fades a node in from transparent, or shows it at once when motion is reduced.
     *
     * @param node the node, already in its shown place
     * @param duration how long the fade takes
     */
    public static void fadeIn(final Node node, final Duration duration) {
        play(node, duration, 0, isReduced());
    }

    /**
     * Brings a node that was already visible back to full strength from a little faded, so a change of state on it is
     * seen (the current navigation step); at once when motion is reduced.
     *
     * @param node the node
     */
    public static void settle(final Node node) {
        play(node, QUICK, SETTLE_START_OPACITY, isReduced());
    }

    /**
     * Fades a card in while it grows from slightly smaller to its size, as a dialog arrives; at once when reduced.
     *
     * @param card the card, already in its shown place
     */
    public static void popIn(final Node card) {
        popIn(card, isReduced());
    }

    static void popIn(final Node card, final boolean reduced) {
        Objects.requireNonNull(card, "card");
        log.debug("card {} arrives, reduced motion {}", card.getId(), reduced);
        if (reduced) {
            card.setOpacity(1);
            card.setScaleX(1);
            card.setScaleY(1);
            return;
        }
        card.setOpacity(0);
        final FadeTransition fade = new FadeTransition(STANDARD, card);
        fade.setToValue(1);
        final ScaleTransition grow = new ScaleTransition(STANDARD, card);
        grow.setFromX(CARD_START_SCALE);
        grow.setFromY(CARD_START_SCALE);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, grow).play();
    }

    static void play(final Node node, final Duration duration, final double from, final boolean reduced) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(duration, "duration");
        log.debug("node {} fades in from {} over {}, reduced motion {}", node.getId(), from, duration, reduced);
        if (reduced) {
            node.setOpacity(1);
            return;
        }
        node.setOpacity(from);
        final FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(from);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * Keeps a progress bar on a fraction, gliding to each new value instead of jumping; a start, a reset, an
     * indeterminate value or reduced motion is shown at once.
     *
     * @param bar the bar; it must not be bound
     * @param fraction the fraction to follow, {@code -1} for indeterminate
     */
    public static void followProgress(final ProgressBar bar, final ObservableValue<? extends Number> fraction) {
        Objects.requireNonNull(bar, "bar");
        Objects.requireNonNull(fraction, "fraction");
        log.debug("progress bar {} follows its fraction", bar.getId());
        bar.setProgress(fraction.getValue().doubleValue());
        fraction.addListener((observed, was, now) -> moveProgress(bar, now.doubleValue(), isReduced()));
    }

    static void moveProgress(final ProgressBar bar, final double target, final boolean reduced) {
        final Object running = bar.getProperties().get(PROGRESS_KEY);
        if (running instanceof Timeline timeline) {
            timeline.stop();
        }
        final double from = bar.getProgress();
        if (reduced || target < 0 || from < 0 || target < from) {
            bar.setProgress(target);
            return;
        }
        final Timeline glide =
                new Timeline(new KeyFrame(SLOW, new KeyValue(bar.progressProperty(), target, Interpolator.EASE_OUT)));
        bar.getProperties().put(PROGRESS_KEY, glide);
        glide.play();
    }

    private static boolean platformReduced() {
        try {
            return Platform.isFxApplicationThread() && Platform.getPreferences().isReducedMotion();
        } catch (final RuntimeException unavailable) {
            log.debug("the platform's reduced-motion preference is unavailable: {}", unavailable.toString());
            return false;
        }
    }
}
