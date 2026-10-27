package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.llm.ModelSelection;

/** Remembers every speed a run handed over. */
final class RecordingRunSpeedSink implements RunSpeedSink {

    /** One speed handed over. */
    record Measured(ModelSelection model, Duration perSegment) {}

    private final List<Measured> seen = new CopyOnWriteArrayList<>();

    @Override
    public void measured(final ModelSelection model, final Duration perSegment) {
        seen.add(new Measured(model, perSegment));
    }

    List<Measured> seen() {
        return List.copyOf(seen);
    }
}
