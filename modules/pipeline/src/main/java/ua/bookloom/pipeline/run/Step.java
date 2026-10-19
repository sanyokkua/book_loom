package ua.bookloom.pipeline.run;

/** A call's answer once the run went on, or how the run ended while it waited for one. */
sealed interface Step<T> {

    /** The call answered. */
    record Done<T>(T value) implements Step<T> {}

    /** The run ended while the call was routed. */
    record Stopped<T>(RunEnd end) implements Step<T> {}
}
