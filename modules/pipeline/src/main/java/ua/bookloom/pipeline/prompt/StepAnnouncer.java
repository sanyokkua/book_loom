package ua.bookloom.pipeline.prompt;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/**
 * Turns the steps of a run into {@code StepsPlanned} and {@code StepChanged} events: each new key gets the next index
 * of its kind, starting from zero, and a step started again under a key seen before keeps its index, so a step
 * retried after a pause is not counted twice. Used from the job thread only.
 */
@Slf4j
public final class StepAnnouncer {

    private static final StepAnnouncer SILENT = new StepAnnouncer(event -> {});

    private final Consumer<JobEvent> sink;
    private final Map<StepKind, Integer> nextIndex = new EnumMap<>(StepKind.class);
    private final Map<String, Open> steps = new LinkedHashMap<>();
    private final Set<String> open = new HashSet<>();

    private StepAnnouncer(final Consumer<JobEvent> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** An announcer that says nothing, for the calls that are no part of a run's steps. */
    public static StepAnnouncer silent() {
        return SILENT;
    }

    /** An announcer that sends every event to {@code sink}. */
    public static StepAnnouncer over(final Consumer<JobEvent> sink) {
        return new StepAnnouncer(sink);
    }

    /** Plans {@code count} steps of {@code kind}; nothing when {@code count} is zero. */
    public void planned(final StepKind kind, final int count, final boolean estimated) {
        Objects.requireNonNull(kind, "kind");
        if (count <= 0) {
            return;
        }
        log.debug("steps planned kind={} count={} estimated={}", kind, count, estimated);
        sink.accept(new StepsPlanned(kind, count, estimated));
    }

    /** Starts the step under {@code key}, or restarts it when it is still open. */
    public void started(final StepKind kind, final String label, final String key, @Nullable final String itemRef) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        final String id = kind + "|" + Objects.requireNonNull(key, "key");
        final Open known = steps.get(id);
        final Open step =
                known != null ? known : new Open(kind, label, key, nextIndex.merge(kind, 1, Integer::sum) - 1, itemRef);
        steps.put(id, step);
        open.add(id);
        emit(new StepChanged(kind, step.label(), step.index(), StepState.STARTED, step.itemRef(), null));
    }

    /** Ends the open step under {@code key}; nothing for a key that was never started. */
    public void ended(final StepKind kind, final String key, final StepResult result) {
        Objects.requireNonNull(result, "result");
        final String id = kind + "|" + Objects.requireNonNull(key, "key");
        final Open step = open.remove(id) ? steps.get(id) : null;
        if (step == null) {
            log.debug("step end ignored, not open kind={} key={}", kind, key);
            return;
        }
        end(step, result);
    }

    /** Plans one step on the first sight of {@code key}, then starts it. */
    public void added(final StepKind kind, final String label, final String key, @Nullable final String itemRef) {
        if (!hasStarted(kind, key)) {
            planned(kind, 1, false);
        }
        started(kind, label, key, itemRef);
    }

    /** Whether a step under {@code key} was ever started, open or ended. */
    public boolean hasStarted(final StepKind kind, final String key) {
        return steps.containsKey(kind + "|" + Objects.requireNonNull(key, "key"));
    }

    /** Starts and ends a step at once. */
    public void instant(
            final StepKind kind,
            final String label,
            final String key,
            @Nullable final String itemRef,
            final StepResult result) {
        started(kind, label, key, itemRef);
        ended(kind, key, result);
    }

    /** Ends every open step of {@code kind} whose key starts with {@code keyPrefix}, in the order they started. */
    public void endOpen(final StepKind kind, final String keyPrefix, final StepResult result) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        final var matching = steps.values().stream()
                .filter(step ->
                        step.kind() == kind && step.key().startsWith(keyPrefix) && open.remove(kind + "|" + step.key()))
                .toList();
        for (final Open step : matching) {
            end(step, result);
        }
    }

    private void end(final Open step, final StepResult result) {
        emit(new StepChanged(step.kind(), step.label(), step.index(), StepState.ENDED, step.itemRef(), result));
    }

    private void emit(final StepChanged event) {
        log.trace("step {}", event);
        sink.accept(event);
    }

    private record Open(
            StepKind kind,
            String label,
            String key,
            int index,
            @Nullable String itemRef) {}
}
