package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * What {@link ProjectService#roundTrip} found comparing a source book against a no-op reassembly of it.
 *
 * @param structurePreserved whether the written copy's structure matches the source's
 * @param idsPreserved whether every id the source carries survived into the written copy
 * @param missingIds the ids the source carries that the written copy lost
 * @param sourceSegments the source's body segment count
 * @param copySegments the written copy's body segment count
 */
public record RoundTripReport(
        boolean structurePreserved,
        boolean idsPreserved,
        List<String> missingIds,
        int sourceSegments,
        int copySegments) {

    /** Rejects a report without its missing-ids list and defensively copies it. */
    public RoundTripReport {
        Objects.requireNonNull(missingIds, "missingIds");
        missingIds = List.copyOf(missingIds);
    }
}
