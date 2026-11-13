package ua.bookloom.pipeline;

import java.util.function.Consumer;
import ua.bookloom.api.pipeline.JobEvent;

/**
 * The parts of a running job the wiring hands to the calls and stages it builds.
 *
 * @param control the job's pause and stop, which aborts a call in flight
 * @param watchdog the clock that calls a model call stuck
 * @param emit where the run's events go
 */
record RunLive(JobControl control, StallWatchdog watchdog, Consumer<JobEvent> emit) {}
