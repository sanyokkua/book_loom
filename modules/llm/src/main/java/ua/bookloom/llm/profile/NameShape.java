package ua.bookloom.llm.profile;

import org.jspecify.annotations.Nullable;

/**
 * A name with its wrapping taken off: the part still to be read as words, and what the wrapping said.
 *
 * @param namespace the publisher path before the last "/" as written, or null
 * @param quantizer the lower-cased namespace when it is a known quantising publisher, else null
 * @param text the words to read, tag and "@variant" folded in with "-", original letter case
 * @param instance the LM Studio ":N" copy number, or null
 * @param unspecified "latest" when the tag was the moving one, or null
 * @param hubDefaultQuant true when a Hugging Face repository was named with no tag and no file, so the engine picks
 *     the quant
 * @param ggufFile true when the name was a ".gguf" file
 * @param sidecar the companion kind of a file ("mmproj", "mtp", "imatrix"), or null
 * @param subfolder the folder that held the file or that the link opened, or null
 * @param file the file name with its extension, or null
 * @param repo the repository {@code org/name} the paste named, or null
 * @param format the weights format a ".safetensors" stem implies, or null
 * @param auxiliary set when the name is no model at all (a README, an importance matrix, one weights shard); the
 *     other fields then carry nothing
 */
record NameShape(
        @Nullable String namespace,
        @Nullable String quantizer,
        String text,
        @Nullable Integer instance,
        @Nullable String unspecified,
        boolean hubDefaultQuant,
        boolean ggufFile,
        @Nullable String sidecar,
        @Nullable String subfolder,
        @Nullable String file,
        @Nullable String repo,
        @Nullable String format,
        @Nullable Auxiliary auxiliary) {

    NameShape withSidecar(final String kind) {
        return new NameShape(
                namespace,
                quantizer,
                text,
                instance,
                unspecified,
                hubDefaultQuant,
                ggufFile,
                kind,
                subfolder,
                file,
                repo,
                format,
                auxiliary);
    }

    /** A shape for a name that is no model; the words of {@code text} are only read when {@code auxiliary} says so. */
    static NameShape ofAuxiliary(final String text, final Auxiliary auxiliary) {
        return new NameShape(null, null, text, null, null, false, false, null, null, null, null, null, auxiliary);
    }
}
