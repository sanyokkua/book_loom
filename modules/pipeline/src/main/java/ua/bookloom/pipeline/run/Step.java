package ua.bookloom.pipeline.run;

import java.util.function.Function;

/** A call's answer once the run went on, or how the run ended while it waited for one. */
sealed interface Step<T> {

    /**
     * Reads the step by which way it went.
     *
     * @param done what to make of the answer
     * @param stopped what to make of the end of the run
     * @param <R> what both produce
     * @return the result of the one that applies
     */
    <R> R fold(Function<? super T, ? extends R> done, Function<? super RunEnd, ? extends R> stopped);

    /**
     * The call answered.
     *
     * @param value the answer
     * @param <T> the answer's type
     */
    record Done<T>(T value) implements Step<T> {

        @Override
        public <R> R fold(
                final Function<? super T, ? extends R> done, final Function<? super RunEnd, ? extends R> stopped) {
            return done.apply(value);
        }
    }

    /**
     * The run ended while the call was routed.
     *
     * @param end how the run ended
     * @param <T> the answer's type the call would have had
     */
    record Stopped<T>(RunEnd end) implements Step<T> {

        @Override
        public <R> R fold(
                final Function<? super T, ? extends R> done, final Function<? super RunEnd, ? extends R> stopped) {
            return stopped.apply(end);
        }
    }
}
