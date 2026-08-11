package ua.bookloom.document.golden;

/**
 * The corpus verification's P5 probe: per-book wall-clock time and source file size (design.md D6, task 11.1).
 *
 * @param wallClockMs the total time every probe for this book took, combined
 * @param sourceFileSizeBytes the source file's size in bytes
 */
record CorpusResourceOutcome(long wallClockMs, long sourceFileSizeBytes) {}
