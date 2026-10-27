package ua.bookloom.api.pipeline;

/**
 * What one brief window answered for one field.
 *
 * @param value the answer in the model's words; never null
 * @param quote the passage the answer rests on as the code found it, or an empty string when it found none; never null
 */
public record SampleAnswer(String value, String quote) {}
