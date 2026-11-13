package ua.bookloom.pipeline.eval;

import org.jspecify.annotations.Nullable;

/**
 * The dataset a report was measured on, as its manifest states it.
 *
 * @param id the dataset id
 * @param version the dataset version
 * @param rootHash the manifest's root hash
 * @param pair the language pair
 * @param ownerData whether the files are the owner's local data
 * @param labelsHash the hash of the dataset's labels file, or null when it has none
 */
record EvalDataset(
        String id,
        int version,
        String rootHash,
        String pair,
        boolean ownerData,
        @Nullable String labelsHash) {

    /** The dataset of a manifest. */
    static EvalDataset of(final DatasetManifest manifest) {
        final String labels = manifest.files().stream()
                .filter(f -> f.path().endsWith("/labels.json"))
                .map(DatasetManifest.FileHash::sha256)
                .findFirst()
                .orElse(null);
        return new EvalDataset(
                manifest.id(), manifest.version(), manifest.rootHash(), manifest.pair(), manifest.ownerData(), labels);
    }
}
