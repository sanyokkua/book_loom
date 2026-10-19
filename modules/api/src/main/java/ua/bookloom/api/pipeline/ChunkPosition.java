package ua.bookloom.api.pipeline;

/**
 * A segment's chunk position within its section, for progress reporting.
 *
 * @param section the 1-based position of the current body unit
 * @param sections the total count of body units
 * @param chunk the 1-based position of the current chunk within the section
 * @param chunks the total chunk count within the section
 */
public record ChunkPosition(int section, int sections, int chunk, int chunks) {}
