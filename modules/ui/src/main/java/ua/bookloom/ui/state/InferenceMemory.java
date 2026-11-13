package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * Where an inference test leaves what it measured.
 *
 * @param speeds where a speed measured by a passing inference test of a saved provider is kept
 * @param structured where whether the model answered in the structured form is kept for the session
 */
public record InferenceMemory(ModelSpeedRecorder speeds, StructuredAnswers structured) {

    /** Rejects a missing part. */
    @Inject
    public InferenceMemory {
        Objects.requireNonNull(speeds, "speeds");
        Objects.requireNonNull(structured, "structured");
    }
}
