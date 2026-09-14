package ua.bookloom.api.pipeline;

/**
 * A lifecycle event emitted by a translation job on its execution thread.
 */
public sealed interface JobEvent permits StageStarted, SegmentDecided, Paused, Resumed, Finished {}
