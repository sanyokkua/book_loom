package ua.bookloom.pipeline.index;

/**
 * The ordinals one unit's segments occupy.
 *
 * @param first the ordinal of its first segment
 * @param last the ordinal of its last segment, inclusive
 */
record UnitSpan(int first, int last) {}
