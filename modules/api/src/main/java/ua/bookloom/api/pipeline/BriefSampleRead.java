package ua.bookloom.api.pipeline;

import java.util.Map;
import java.util.Objects;

/**
 * One window of the brief suggestion was read, so a progress dialog can show each answer beside its quote.
 *
 * @param sample the window's position, from 1
 * @param samples how many windows the suggestion reads
 * @param answers what the window answered per field; copied, never null, empty when its reply could not be read
 * @param location where the window lies in the book, as {@code ch1 · p02–p14}; empty when unknown, never null
 * @param sent the text the model was sent for this window; empty when none, never null
 */
public record BriefSampleRead(
        int sample, int samples, Map<BriefField, SampleAnswer> answers, String location, String sent)
        implements JobEvent {

    /** Rejects a position outside the windows and copies the answers. */
    public BriefSampleRead {
        if (sample < 1 || sample > samples) {
            throw new IllegalArgumentException("sample " + sample + " is not within 1.." + samples);
        }
        answers = Map.copyOf(Objects.requireNonNull(answers, "answers"));
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(sent, "sent");
    }
}
