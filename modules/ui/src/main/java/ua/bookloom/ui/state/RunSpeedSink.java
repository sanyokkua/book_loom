package ua.bookloom.ui.state;

import java.time.Duration;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSelection;

/** Takes the speed a run measured on its model when the run ends. */
@FunctionalInterface
interface RunSpeedSink {

    /** Keeps nothing: the sink of a runner built without a recorder. */
    RunSpeedSink NONE = (model, perSegment) -> {};

    /**
     * Receives the speed of one ended run.
     *
     * @param model the provider and model the run used
     * @param perSegment the mean time of one drafted segment, pauses left out
     */
    void measured(ModelSelection model, Duration perSegment);

    /**
     * Keeps each run's speed as the model's stored speed, marked as measured by a run.
     *
     * @param recorder where the speeds are stored
     * @return the sink
     */
    static RunSpeedSink storingIn(final ModelSpeedRecorder recorder) {
        return (model, perSegment) ->
                recorder.record(model.providerId(), model.modelId(), perSegment, MeasuredFrom.RUN);
    }
}
