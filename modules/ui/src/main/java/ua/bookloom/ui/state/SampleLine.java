package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * What one sample answered for a decided field, beside the place it was read and the quote from the book.
 *
 * @param sample the sample's number, from 1
 * @param location where the sample lies in the book, or empty when unknown
 * @param value the sample's answer in its own words, or empty when it gave none
 * @param quote the book's words the answer rests on, or empty when it quoted none
 * @param verified whether the answer has a quote, so it counts; an answer without one is shown and never used
 */
public record SampleLine(int sample, String location, String value, String quote, boolean verified) {

    /** Rejects a missing text. */
    public SampleLine {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(quote, "quote");
    }
}
