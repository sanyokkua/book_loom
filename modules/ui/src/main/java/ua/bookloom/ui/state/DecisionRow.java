package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.pipeline.BriefField;

/**
 * One row of "What it decides" in the fill-in dialog.
 *
 * @param group the field the row stands for: genre, register, who tells the story, or who reads it (the audience, with
 *     the voice in its detail)
 * @param value the value in words, or empty while no sample has answered it
 * @param agreeing how many of the samples support the value
 * @param samples how many samples were read (so far, while the last step has not decided)
 * @param isUncertain whether the samples did not agree on the value, so the brief keeps what it has
 * @param samplesRead each sample's answer with its place and quote, in reading order; never null
 */
public record DecisionRow(
        BriefField group, String value, int agreeing, int samples, boolean isUncertain, List<SampleLine> samplesRead) {

    /** Rejects a missing part and copies the lines. */
    public DecisionRow {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(value, "value");
        samplesRead = List.copyOf(Objects.requireNonNull(samplesRead, "samplesRead"));
    }
}
