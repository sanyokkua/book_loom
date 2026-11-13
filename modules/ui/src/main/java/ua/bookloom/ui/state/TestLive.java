package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What the running provider test has said so far.
 *
 * @param sent its requests, oldest first
 * @param started its stages that have begun, ended or not
 * @param finished its stages that have ended
 * @param steps how many stages the test covers
 * @param now the time the card shows a wait up to
 */
public record TestLive(
        List<SentCall> sent, List<StageStart> started, List<StageChip> finished, int steps, Instant now) {

    /** Nothing said: a test not running, or one that has sent nothing yet. */
    static final TestLive NONE = new TestLive(List.of(), List.of(), List.of(), 0, Instant.EPOCH);

    /** Copies the lists and rejects a missing part. */
    public TestLive {
        sent = List.copyOf(sent);
        started = List.copyOf(started);
        finished = List.copyOf(finished);
        Objects.requireNonNull(now, "now");
    }
}
