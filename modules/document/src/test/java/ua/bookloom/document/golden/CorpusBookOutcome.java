package ua.bookloom.document.golden;

import java.util.Objects;

/**
 * One book's outcome across all five probes design.md D6 specifies, keyed by file name — the corpus is
 * third-party copyrighted material, so the file name (never its content) is the only identity a report carries.
 *
 * @param fileName the book's path relative to the configured corpus directory, {@code /}-separated on every
 *     platform — the report's row key, so two runs can be diffed row-for-row (design.md D6a). Deliberately not
 *     the bare file name: the corpus is a tree and does contain two different books sharing one base name.
 * @param open the P0 probe's outcome
 * @param identity the P1 probe's outcome
 * @param fixedPoint the P2 probe's outcome
 * @param mutation the P3 probe's outcome
 * @param idempotence the P4 probe's outcome
 * @param mask the mask-then-restore probe's outcome (task 10.1)
 * @param resource the P5 probe's outcome
 */
record CorpusBookOutcome(
        String fileName,
        CorpusOpenOutcome open,
        CorpusIdentityOutcome identity,
        CorpusFixedPointOutcome fixedPoint,
        CorpusMutationOutcome mutation,
        CorpusIdempotenceOutcome idempotence,
        CorpusMaskOutcome mask,
        CorpusResourceOutcome resource) {

    CorpusBookOutcome {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(fixedPoint, "fixedPoint");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(idempotence, "idempotence");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(resource, "resource");
    }
}
