package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;

/**
 * What one progress dialog shows, kept while its work runs so the dialog reopens as it was: the steps, the selected
 * one, whether the selection follows the running step, the Simple or Detailed view, the figures and the model calls
 * each step made.
 *
 * <p>The selection is moved by the work only while follow is on; only a person's choice ({@link #choose}) turns follow
 * off, and turning it on again selects the running step. A call is kept with the step that was running when it first
 * appeared. Full prompts are kept for the last {@value #FULL_PROMPTS} calls only; an older call keeps its reply. FX
 * thread only.
 */
@Slf4j
public final class ProgressDialogModel {

    /** How many of the newest calls keep their full prompt. */
    static final int FULL_PROMPTS = 50;

    /** The two views of a selected step. */
    public enum Mode {
        /** One plain sentence and what the step read and wrote. */
        SIMPLE,
        /** The call, its clock and tokens, and the parsed prompt and reply as well. */
        DETAILED
    }

    private final ProgressFigures figuresOf;
    private final Supplier<Instant> now;
    private final boolean hideable;
    private final ObservableList<StepEntry> rows = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<@Nullable StepEntry> selected = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty follow = new SimpleBooleanProperty(true);
    private final ObjectProperty<Mode> mode;
    private final ReadOnlyObjectWrapper<ProgressFigures.Figures> figures =
            new ReadOnlyObjectWrapper<>(ProgressFigures.Figures.NONE);
    private final ReadOnlyObjectWrapper<Activity> activity;
    private final Map<String, Long> callOfStep = new HashMap<>();
    private final Map<Long, CallSnapshot> calls = new HashMap<>();
    private final Deque<Long> fullPrompts = new ArrayDeque<>();
    private final Set<Long> dropped = new HashSet<>();
    private @Nullable String selectedKey;

    /**
     * Builds the model of one activity's dialog and reads the activity once.
     *
     * @param first the activity as it is now
     * @param figuresOf how the figures line is read from the steps
     * @param initial the view the dialog opens in
     * @param now the clock the figures are read at
     */
    public ProgressDialogModel(
            final Activity first, final ProgressFigures figuresOf, final Mode initial, final Supplier<Instant> now) {
        Objects.requireNonNull(first, "first");
        this.figuresOf = Objects.requireNonNull(figuresOf, "figuresOf");
        this.now = Objects.requireNonNull(now, "now");
        this.mode = new SimpleObjectProperty<>(Objects.requireNonNull(initial, "initial"));
        this.hideable = first.kind().isHideable();
        this.activity = new ReadOnlyObjectWrapper<>(first);
        follow.addListener((observed, was, on) -> {
            log.debug("progress dialog #{}: follow turned {}", first.id(), on ? "on" : "off");
            if (on) {
                selectRunning();
            }
        });
        accept(first);
    }

    /**
     * Reads the activity again: its steps, its calls and its figures; the selection moves only while follow is on.
     *
     * @param now the activity as it is now; the same id as the first
     */
    public void accept(final Activity now) {
        Objects.requireNonNull(now, "now");
        activity.set(now);
        final StepLog log = now.steps();
        attach(log.running(), now.calls().previous());
        attach(log.running(), now.calls().current());
        final List<StepEntry> next = log.rows();
        if (!next.equals(rows)) {
            rows.setAll(next);
        }
        figures.set(steady(figuresOf.of(log, this.now.get())));
        if (follow.get()) {
            selectRunning();
        } else {
            reselect();
        }
    }

    /**
     * A person chose a step: it is selected and follow turns off.
     *
     * @param step the chosen row
     */
    public void choose(final StepEntry step) {
        Objects.requireNonNull(step, "step");
        log.debug("progress dialog: the person chose step {}", step.key());
        selectedKey = step.key();
        follow.set(false);
        reselect();
    }

    private void selectRunning() {
        final StepEntry running = activity.get().steps().running();
        if (running != null) {
            selectedKey = running.key();
        }
        reselect();
    }

    private void reselect() {
        final String key = selectedKey;
        selected.set(
                key == null
                        ? null
                        : rows.stream()
                                .filter(row -> row.key().equals(key))
                                .findFirst()
                                .orElse(null));
    }

    private ProgressFigures.Figures steady(final ProgressFigures.Figures read) {
        final ProgressFigures.Figures before = figures.get();
        return read.percent() >= before.percent()
                ? read
                : read.holding(before.percent(), Math.max(before.fraction(), read.fraction()));
    }

    private void attach(final @Nullable StepEntry running, final @Nullable CallSnapshot call) {
        if (call == null) {
            return;
        }
        final long id = call.callId();
        final boolean known = calls.containsKey(id);
        calls.put(id, dropped.contains(id) ? withoutPrompt(call) : call);
        if (known) {
            return;
        }
        if (running != null) {
            callOfStep.putIfAbsent(running.key(), id);
        }
        fullPrompts.addLast(id);
        if (fullPrompts.size() > FULL_PROMPTS) {
            final long oldest = fullPrompts.removeFirst();
            dropped.add(oldest);
            calls.computeIfPresent(oldest, (key, kept) -> withoutPrompt(kept));
        }
    }

    private static CallSnapshot withoutPrompt(final CallSnapshot call) {
        return new CallSnapshot(
                call.callId(),
                call.kind(),
                call.label(),
                call.position(),
                call.segments(),
                List.of(),
                List.of(),
                call.reply(),
                call.state(),
                call.startedAt(),
                call.elapsed(),
                call.attempt(),
                call.maxAttempts(),
                call.timeout(),
                call.usage(),
                call.outcomes(),
                call.items());
    }

    /**
     * The model call a step made.
     *
     * @param step the row
     * @return the call as last seen, or {@code null} when the step made none that was seen
     */
    public @Nullable CallSnapshot callOf(final StepEntry step) {
        final Long id = callOfStep.get(Objects.requireNonNull(step, "step").key());
        return id == null ? null : calls.get(id);
    }

    /**
     * Whether a step's call is old enough that its full prompt is no longer kept.
     *
     * @param step the row
     * @return {@code true} when its prompt was dropped, {@code false} otherwise
     */
    public boolean isPromptDropped(final StepEntry step) {
        final Long id = callOfStep.get(Objects.requireNonNull(step, "step").key());
        return id != null && dropped.contains(id);
    }

    /** {@return the rows, steps announced then steps planned; never null} */
    public ObservableList<StepEntry> rows() {
        return rows;
    }

    /** {@return the selected row, holding {@code null} while none is selected} */
    public ReadOnlyObjectProperty<@Nullable StepEntry> selected() {
        return selected.getReadOnlyProperty();
    }

    /** {@return whether the selection follows the running step; a person's choice turns it off} */
    public BooleanProperty follow() {
        return follow;
    }

    /** {@return the view the selected step is shown in} */
    public ObjectProperty<Mode> mode() {
        return mode;
    }

    /** {@return the figures, whose percentage never falls} */
    public ReadOnlyObjectProperty<ProgressFigures.Figures> figures() {
        return figures.getReadOnlyProperty();
    }

    /** {@return the activity as last read} */
    public ReadOnlyObjectProperty<Activity> activity() {
        return activity.getReadOnlyProperty();
    }

    /**
     * Whether the work may continue in the background.
     *
     * @return {@code true} for long work, {@code false} otherwise
     */
    public boolean isHideable() {
        return hideable;
    }
}
