package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.control.HowCountedPane;
import ua.bookloom.ui.control.Motion;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProgressFigures;
import ua.bookloom.ui.state.RunFigures;
import ua.bookloom.ui.state.RunRow;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.StartEstimate;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;

/**
 * The summary strip on top of the Translating screen in every state: one big line (the time left, how long a finished
 * run took, the estimate before a start, or the run's state), the bar of all the work, the segments done with the
 * chapter and the time elapsed, the counts by outcome, "n to review", and the closed "How progress is counted".
 *
 * <p>Every label is bound to the mirror and none logs: they change with every batch of decisions and every pace tick,
 * which is the case the logging rule forbids logging in a listener for. A part of a line is left out while it is
 * unknown, so an unstarted run shows no "Chapter 0 of 0".
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingSummaryStrip {

    private static final double SPACING = 8;
    private static final double COUNT_GAP = 16;
    private static final String PART_SEPARATOR = " · ";
    private static final String ERR_BAR = "bar-err";
    private static final String FRACTION_KEY = "bookloom.progress.fraction";

    /**
     * What the strip reads besides the mirror.
     *
     * @param runs the run-state row
     * @param decided the segments decided: the stored project's before a run
     * @param total the segments in all: the stored project's before a run
     * @param estimate the time the book is expected to take before a start
     * @param flagged the segments flagged for review, as the desk or the run counts them
     * @param onReview what "n to review" does
     */
    record Inputs(
            ReadOnlyObjectProperty<RunStateView> runs,
            ObservableIntegerValue decided,
            ObservableIntegerValue total,
            ReadOnlyObjectProperty<StartEstimate> estimate,
            ObservableIntegerValue flagged,
            Runnable onReview) {}

    static Node build(final StateMirror mirror, final Inputs inputs, final Messages messages) {
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(messages, "messages");
        log.debug("building the translating strip");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox top =
                new HBox(SPACING, bigLine(mirror, inputs, messages), spacer, reviewButton(mirror, inputs, messages));
        top.setAlignment(Pos.CENTER_LEFT);
        final HowCountedPane how = new HowCountedPane("translating-how-counted", messages);
        how.show(mirror.live().progress().get().kinds());
        final ChangeListener<ProgressFigures.Figures> onSteps = (observed, was, now) -> how.show(now.kinds());
        // The fold keeps its listener, which the mirror holds weakly, so a replaced screen is not kept alive.
        how.getProperties().put(ChangeListener.class, onSteps);
        mirror.live().progress().addListener(new WeakChangeListener<>(onSteps));
        final VBox strip = new VBox(
                SPACING,
                top,
                bar(mirror, inputs, messages),
                factsLine(mirror, inputs, messages),
                counts(mirror, messages),
                how);
        strip.setId("translating-strip");
        strip.getStyleClass().addAll("card", "strip");
        return strip;
    }

    private static Label bigLine(final StateMirror mirror, final Inputs inputs, final Messages messages) {
        final Label big = bound(
                "translating-strip-big",
                "strip-big",
                () -> bigText(
                        inputs.runs().get(),
                        mirror.live().throughput().get(),
                        inputs.estimate().get(),
                        messages),
                inputs.runs(),
                mirror.live().throughput(),
                inputs.estimate());
        // The Ukrainian estimate before Start is wider than the smallest window: it breaks instead of losing its end.
        big.setWrapText(true);
        big.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(big, Priority.SOMETIMES);
        Visibility.bind(big, big.textProperty().isNotEmpty());
        return big;
    }

    static String bigText(
            final RunStateView view, final Throughput pace, final StartEstimate estimate, final Messages messages) {
        final RunRow row = view.row();
        return switch (row) {
            case TRANSLATING, STUCK ->
                pace.timeLeft() == null
                        ? view.title().in(messages)
                        : messages.get(MessageKey.TRANSLATING_PACE_LEFT, DurationText.about(messages, pace.timeLeft()));
            case FINISHED_TO_REVIEW, FINISHED_NONE ->
                pace.elapsed().isZero()
                        ? view.title().in(messages)
                        : messages.get(
                                MessageKey.TRANSLATING_STRIP_FINISHED_IN,
                                DurationText.spelled(messages, pace.elapsed()));
            case READY, CANNOT_START ->
                estimate instanceof StartEstimate.Measured measured
                        ? measured.lines(messages).getFirst()
                        : "";
            default -> view.title().in(messages);
        };
    }

    private static Button reviewButton(final StateMirror mirror, final Inputs inputs, final Messages messages) {
        final Button review = Tips.install(messages, new Button(), MessageKey.TRANSLATING_STRIP_REVIEW_TIP);
        review.setId("translating-strip-review");
        review.getStyleClass().add("btn-secondary");
        review.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                MessageKey.TRANSLATING_STRIP_REVIEW,
                                inputs.flagged().get()),
                        inputs.flagged()));
        final BooleanBinding offered = Bindings.createBooleanBinding(
                () -> inputs.flagged().get() > 0 && mirror.runState().get() != RunState.IDLE,
                inputs.flagged(),
                mirror.runState());
        Visibility.bind(review, offered);
        review.setOnAction(event -> {
            log.debug("strip: {} to review pressed", inputs.flagged().get());
            inputs.onReview().run();
        });
        return review;
    }

    private static ProgressBar bar(final StateMirror mirror, final Inputs inputs, final Messages messages) {
        final ProgressBar bar = new ProgressBar();
        bar.setId("translating-strip-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        followSteps(bar, mirror, inputs, messages);
        followProviderError(bar, mirror);
        return bar;
    }

    // The bar and its tooltip read the same figures, so the steps the tooltip counts are the share the bar shows.
    private static void followSteps(
            final ProgressBar bar, final StateMirror mirror, final Inputs inputs, final Messages messages) {
        final ReadOnlyObjectProperty<ProgressFigures.Figures> steps =
                mirror.live().progress();
        // Before a run there are no steps: the bar shows how much of the stored project is decided.
        final DoubleBinding fraction = Bindings.createDoubleBinding(
                () -> mirror.runState().get() == RunState.IDLE
                                && mirror.figures().get().total() == 0
                        ? storedFraction(inputs)
                        : steps.get().fraction(),
                steps,
                mirror.runState(),
                mirror.figures(),
                inputs.decided(),
                inputs.total());
        // A binding is held weakly by what it watches; the bar keeps it, or the bar would stop following.
        bar.getProperties().put(FRACTION_KEY, fraction);
        Motion.followProgress(bar, fraction);
        Tips.installLive(
                bar,
                Bindings.createStringBinding(
                        () -> messages.get(
                                MessageKey.TRANSLATING_STRIP_BAR_STEPS,
                                steps.get().percent(),
                                steps.get().done(),
                                steps.get().total()),
                        steps));
    }

    private static double storedFraction(final Inputs inputs) {
        final int total = inputs.total().get();
        return total == 0 ? 0.0 : (double) inputs.decided().get() / total;
    }

    // The danger role belongs to the pause on an error only: a resume clears the error, and any other state drops it.
    private static void followProviderError(final ProgressBar bar, final StateMirror mirror) {
        final BooleanBinding failing = Bindings.createBooleanBinding(
                () -> mirror.runState().get() == RunState.PAUSED
                        && mirror.review().providerError().get() != null,
                mirror.runState(),
                mirror.review().providerError());
        bar.getProperties().put(BooleanBinding.class, failing);
        final Runnable paint = () -> {
            bar.getStyleClass().remove(ERR_BAR);
            if (failing.get()) {
                bar.getStyleClass().add(ERR_BAR);
            }
        };
        failing.addListener((observed, was, now) -> paint.run());
        paint.run();
    }

    private static Label factsLine(final StateMirror mirror, final Inputs inputs, final Messages messages) {
        final Label line = bound(
                "translating-strip-line",
                "strip-line",
                () -> factsText(
                        mirror.figures().get(),
                        inputs.decided().get(),
                        inputs.total().get(),
                        mirror.live().throughput().get(),
                        messages),
                inputs.decided(),
                inputs.total(),
                mirror.figures(),
                mirror.live().throughput());
        Tips.install(messages, line, MessageKey.TRANSLATING_STRIP_DONE_TIP);
        line.setWrapText(true);
        return line;
    }

    // A run's own figures win once it has counted anything; before that the stored project's counts stand.
    static String factsText(
            final RunFigures figures,
            final int storedDecided,
            final int storedTotal,
            final Throughput pace,
            final Messages messages) {
        final boolean counted = figures.total() > 0;
        final List<String> parts = new ArrayList<>();
        parts.add(messages.get(
                MessageKey.TRANSLATING_STRIP_DONE,
                counted ? figures.total() - figures.remaining() : storedDecided,
                counted ? figures.total() : storedTotal));
        if (figures.sections() > 0) {
            parts.add(messages.get(MessageKey.TRANSLATING_LINE_SECTION, figures.section(), figures.sections()));
        }
        if (!pace.elapsed().isZero()) {
            parts.add(
                    messages.get(MessageKey.TRANSLATING_STRIP_ELAPSED, DurationText.format(messages, pace.elapsed())));
        }
        return String.join(PART_SEPARATOR, parts);
    }

    private static FlowPane counts(final StateMirror mirror, final Messages messages) {
        final ReadOnlyObjectProperty<RunFigures> figures = mirror.figures();
        final Label translated = figure(
                "translated", MessageKey.TRANSLATING_STRIP_TRANSLATED, RunFigures::autoAccepted, figures, messages);
        final Label fixed =
                figure("fixed", MessageKey.TRANSLATING_STRIP_FIXED, RunFigures::repaired, figures, messages);
        final Label toReview =
                figure("to-review", MessageKey.TRANSLATING_STRIP_TO_REVIEW, RunFigures::flagged, figures, messages);
        final Label kept = count(
                "kept",
                MessageKey.TRANSLATING_STRIP_KEPT,
                messages,
                () -> figures.get().keptVerbatim() + mirror.live().sourceKept().get(),
                figures,
                mirror.live().sourceKept());
        final Label secondLook = secondLook(mirror, messages);
        final FlowPane row = new FlowPane(COUNT_GAP, SPACING / 2, translated, fixed, toReview, kept, secondLook);
        row.setId("translating-counts");
        // A book not yet started has nothing to count.
        Visibility.bind(row, mirror.runState().isNotEqualTo(RunState.IDLE));
        return row;
    }

    // The counts of the run's figures: translated untouched, fixed by a repair, flagged.
    private static Label figure(
            final String name,
            final MessageKey key,
            final ToIntFunction<RunFigures> value,
            final ReadOnlyObjectProperty<RunFigures> figures,
            final Messages messages) {
        return count(name, key, messages, () -> value.applyAsInt(figures.get()), figures);
    }

    // Shown only while the final audit doubts an accepted segment.
    private static Label secondLook(final StateMirror mirror, final Messages messages) {
        final Label secondLook = count(
                "second-look",
                MessageKey.TRANSLATING_STRIP_SECOND_LOOK,
                messages,
                () -> mirror.live().suspicious().get(),
                mirror.live().suspicious());
        secondLook.getStyleClass().add("strip-count-warn");
        Tips.install(messages, secondLook, MessageKey.TRANSLATING_STRIP_SECOND_LOOK_TIP);
        Visibility.bind(secondLook, mirror.live().suspicious().greaterThan(0));
        return secondLook;
    }

    private static Label count(
            final String name,
            final MessageKey key,
            final Messages messages,
            final Callable<Integer> value,
            final Observable... sources) {
        final Label label =
                bound("translating-count-" + name, "strip-count", () -> messages.get(key, value.call()), sources);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label bound(
            final String id, final String styleClass, final Callable<String> text, final Observable... sources) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add(styleClass);
        label.textProperty().bind(Bindings.createStringBinding(text, sources));
        return label;
    }
}
