package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/**
 * Carries the model calls and the steps of one piece of blocking work, such as a glossary scan or a setup proposal,
 * onto its progress dialog: each call snapshot the work announces becomes the current call, the one before it the
 * previous one, and each step event joins the activity's steps.
 *
 * <p>Work that announces no steps of its own (the file name suggestion, the card actions of Names &amp; style) gets one
 * request step per call, started when the call is first seen and ended when it is answered or stopped, so its dialog
 * still lists what it did. FX thread only, like the activity it feeds.
 */
@Slf4j
final class CallFeed {

    private final ActivityTracker.Handle handle;
    private final Supplier<Instant> now;
    private final Consumer<BriefSampleRead> samples;
    private final LiveCallState calls = new LiveCallState();
    private final Set<Long> stepsOfCalls = new HashSet<>();
    private final Map<String, StepChanged> latest = new HashMap<>();
    private final Map<String, StepEntry.StepText> texts = new HashMap<>();
    private boolean announcesSteps;

    CallFeed(final ActivityTracker.Handle handle, final Supplier<Instant> now) {
        this(handle, now, read -> {});
    }

    /**
     * A feed that also hands what each brief sample answered to a receiver, which builds "What it decides".
     *
     * @param samples receives each {@link BriefSampleRead} in order, on the FX thread
     */
    CallFeed(
            final ActivityTracker.Handle handle, final Supplier<Instant> now, final Consumer<BriefSampleRead> samples) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.now = Objects.requireNonNull(now, "now");
        this.samples = Objects.requireNonNull(samples, "samples");
    }

    /** Shows a call snapshot or a step event, hands a sample's answer on, and ignores any other event. */
    void accept(final JobEvent event) {
        if (event instanceof BriefSampleRead read) {
            log.debug("brief sample {} of {} answered", read.sample(), read.samples());
            samples.accept(read);
            showSent(read);
            return;
        }
        if (event instanceof StepChanged changed) {
            announcesSteps = true;
            latest.put(changed.label(), changed);
            handle.step(changed, texts.get(changed.label()));
            return;
        }
        if (event instanceof StepsPlanned) {
            announcesSteps = true;
            handle.steps(event);
            return;
        }
        if (event instanceof CallSnapshotUpdated updated) {
            final CallSnapshot snapshot = updated.snapshot();
            log.debug("work call {} {} shown on the progress dialog", snapshot.callId(), snapshot.state());
            calls.snapshot(snapshot);
            handle.calls(calls.view(now.get()));
            if (!announcesSteps) {
                stepOfCall(snapshot);
            }
        }
    }

    // The sample's step has ended by the time its answer arrives; it keeps its state and gains what it was sent.
    private void showSent(final BriefSampleRead read) {
        if (read.sent().isBlank()) {
            return;
        }
        final String label = StepEntry.SAMPLE_LABEL + read.sample();
        final String sent = read.location().isEmpty() ? read.sent() : read.location() + "\n" + read.sent();
        final StepEntry.StepText text = new StepEntry.StepText(sent, null, null);
        texts.put(label, text);
        final StepChanged step = latest.get(label);
        if (step != null) {
            handle.step(step, text);
        }
    }

    private void stepOfCall(final CallSnapshot snapshot) {
        final long id = snapshot.callId();
        final int index = (int) id - 1;
        if (stepsOfCalls.add(id)) {
            log.debug("work call {} becomes request step {}", id, index);
            handle.steps(new StepsPlanned(StepKind.REQUEST, 1, true));
            handle.steps(new StepChanged(StepKind.REQUEST, snapshot.label(), index, StepState.STARTED, null, null));
        }
        // A failed attempt may be followed by another, so only an answer or a stop ends the step.
        final @Nullable StepResult result =
                switch (snapshot.state()) {
                    case WAITING, FAILED -> null;
                    case ANSWERED -> StepResult.DONE;
                    case CANCELLED -> StepResult.FAILED;
                };
        if (result != null) {
            handle.steps(new StepChanged(StepKind.REQUEST, snapshot.label(), index, StepState.ENDED, null, result));
        }
    }
}
