package ua.bookloom.ui.state;

import java.time.Duration;
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
import ua.bookloom.api.pipeline.CallState;

/**
 * What one progress dialog shows, kept while its work runs so the dialog reopens as it was: the steps, the selected
 * one, whether the selection follows the running step, the Simple or Detailed view, the figures and the model calls
 * each step made.
 *
 * <p>The selection is moved by the work only while follow is on; only a person's choice ({@link #choose}) turns follow
 * off, and turning it on again selects the running step. A call is kept with the step that was running when it first
 * appeared, or with the request step the work says sent it. A stage row shows its requests as a list: the request
 * followed is the newest, held for {@value #HOLD_MILLIS} ms after its reply so it can be read, or the one the person
 * chose. Full prompts are kept for the last {@value #FULL_PROMPTS} calls only; an older call keeps its reply. FX thread
 * only.
 */
@Slf4j
public final class ProgressDialogModel {

    /** How many of the newest calls keep their full prompt. */
    static final int FULL_PROMPTS = 50;

    /** How long an answered request stays on show before the next one is followed, so its answer can be read. */
    static final long HOLD_MILLIS = 1500;

    private static final Duration HOLD = Duration.ofMillis(HOLD_MILLIS);

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
    private final Map<Long, Instant> answeredAt = new HashMap<>();
    private final Map<String, String> focus = new HashMap<>();
    private final Map<String, String> chosen = new HashMap<>();
    private final ObservableList<StepEntry> requestRows = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<@Nullable StepEntry> focusedRequest = new ReadOnlyObjectWrapper<>();
    private StageRows stageRows = new StageRows(List.of(), Map.of());
    private Map<String, String> lastRequestOfStage = Map.of();
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
                chosen.clear();
                refocus();
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
        final StepLog log = now.steps();
        attachCalls(now);
        final StageRows stages = StageRows.of(log);
        stageRows = stages;
        lastRequestOfStage = stages.lastRequest();
        refocus();
        final List<StepEntry> next = stages.rows().isEmpty() ? log.rows() : stages.rows();
        if (!next.equals(rows)) {
            rows.setAll(next);
        }
        figures.set(steady(figuresOf.of(log, this.now.get())));
        // Set last of the data it reads, so a listener of the activity sees the calls and rows of this update.
        activity.set(now);
        if (follow.get()) {
            selectRunning();
        } else {
            reselect();
        }
    }

    /**
     * Lets the followed request move on once its reply has been on show long enough; the dialog calls it as its clock
     * ticks, since nothing else happens between two requests.
     */
    public void advance() {
        refocus();
        showRequests();
    }

    /**
     * A person chose a request of the selected stage: it is shown and follow turns off.
     *
     * @param request the chosen request row
     */
    public void chooseRequest(final StepEntry request) {
        Objects.requireNonNull(request, "request");
        final StepEntry stage = selected.get();
        if (stage == null) {
            return;
        }
        log.debug("progress dialog: the person chose request {} of stage {}", request.key(), stage.key());
        chosen.put(stage.key(), request.key());
        follow.set(false);
        refocus();
        showRequests();
    }

    // Each stage shows its newest request, kept a moment after its reply, unless the person chose one.
    private void refocus() {
        stageRows.requests().forEach((stage, sent) -> {
            final String last = sent.getLast().key();
            final String pick = sent.stream().anyMatch(request -> request.key().equals(chosen.get(stage)))
                    ? chosen.get(stage)
                    : heldOr(stage, last);
            focus.put(stage, pick);
        });
    }

    private String heldOr(final String stage, final String last) {
        final String held = focus.get(stage);
        return follow.get() && held != null && !held.equals(last) && isHeld(held) ? held : last;
    }

    private boolean isHeld(final String request) {
        final Long id = callOfStep.get(request);
        final Instant answered = id == null ? null : answeredAt.get(id);
        return answered != null && Duration.between(answered, now.get()).compareTo(HOLD) < 0;
    }

    private void showRequests() {
        final StepEntry row = selected.get();
        final List<StepEntry> sent =
                row == null ? List.of() : stageRows.requests().getOrDefault(row.key(), List.of());
        if (!sent.equals(requestRows)) {
            requestRows.setAll(sent);
        }
        final String key = row == null ? null : focus.get(row.key());
        focusedRequest.set(sent.stream()
                .filter(request -> request.key().equals(key))
                .findFirst()
                .orElse(null));
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

    // The export's next reread has nothing to show until its answer arrives, so its dialog follows the last item read.
    private void selectRunning() {
        final StepEntry running = activity.get().kind() == ActivityKind.EXPORT
                ? lastItem().orElse(activity.get().steps().running())
                : activity.get().steps().running();
        if (running != null) {
            selectedKey = stageRowKey(running);
        } else if (selectedKey == null && !rows.isEmpty()) {
            // Work that ended before the dialog read it (a quick answer) still shows its last step.
            selectedKey = rows.getLast().key();
        }
        reselect();
    }

    // A request in flight has no row of its own; its stage's row is the one that shows it, so that row is followed.
    private String stageRowKey(final StepEntry running) {
        return lastRequestOfStage.entrySet().stream()
                .filter(stage -> stage.getValue().equals(running.key()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(running.key());
    }

    private java.util.Optional<StepEntry> lastItem() {
        return activity.get().steps().entries().reversed().stream()
                .filter(entry -> entry.itemRef() != null && entry.text() != null)
                .findFirst();
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
        showRequests();
    }

    private ProgressFigures.Figures steady(final ProgressFigures.Figures read) {
        final ProgressFigures.Figures before = figures.get();
        return read.percent() >= before.percent()
                ? read
                : read.holding(before.percent(), Math.max(before.fraction(), read.fraction()));
    }

    // Work that says which request sent each call is read exactly; any other work's call belongs to the running step.
    private void attachCalls(final Activity now) {
        final LiveCalls live = now.calls();
        if (live.requestOf().isEmpty()) {
            final StepEntry running = now.steps().running();
            attach(running == null ? null : running.key(), live.previous());
            attach(running == null ? null : running.key(), live.current());
            return;
        }
        live.recent().forEach(call -> attach(live.requestOf().get(call.callId()), call));
    }

    private void attach(final @Nullable String stepKey, final @Nullable CallSnapshot call) {
        if (call == null) {
            return;
        }
        final long id = call.callId();
        final boolean known = calls.containsKey(id);
        calls.put(id, dropped.contains(id) ? withoutPrompt(call) : call);
        if (call.state() == CallState.ANSWERED) {
            answeredAt.putIfAbsent(id, now.get());
        }
        if (known) {
            return;
        }
        if (stepKey != null) {
            callOfStep.putIfAbsent(stepKey, id);
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
                call.items(),
                null,
                null);
    }

    /**
     * The model call a step made.
     *
     * @param step the row
     * @return the call as last seen, or {@code null} when the step made none that was seen
     */
    public @Nullable CallSnapshot callOf(final StepEntry step) {
        final Long id = callOfStep.get(callKey(step));
        return id == null ? null : calls.get(id);
    }

    /**
     * Whether a step's call is old enough that its full prompt is no longer kept.
     *
     * @param step the row
     * @return {@code true} when its prompt was dropped, {@code false} otherwise
     */
    public boolean isPromptDropped(final StepEntry step) {
        final Long id = callOfStep.get(callKey(step));
        return id != null && dropped.contains(id);
    }

    // A stage row shows the call of the request it follows.
    private String callKey(final StepEntry step) {
        final String key = Objects.requireNonNull(step, "step").key();
        return focus.getOrDefault(key, key);
    }

    /** {@return the requests of the selected stage, in the order sent; empty when the selection is no stage} */
    public ObservableList<StepEntry> requestRows() {
        return requestRows;
    }

    /** {@return the request of the selected stage whose call is shown, holding {@code null} when there is none} */
    public ReadOnlyObjectProperty<@Nullable StepEntry> focusedRequest() {
        return focusedRequest.getReadOnlyProperty();
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
