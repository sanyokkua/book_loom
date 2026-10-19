package ua.bookloom.pipeline.checks;

/**
 * An opening and a closing quote mark.
 *
 * @param open the mark that opens quoted speech
 * @param close the mark that closes it
 */
public record QuotePair(char open, char close) {}
