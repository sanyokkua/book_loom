package ua.bookloom.llm.profile;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelKind;

/**
 * A name that is not a chat model: a companion file, a weights shard or a document.
 *
 * @param kind what it stands for
 * @param sidecar "imatrix" for an importance matrix, else null
 * @param format "safetensors" for a weights shard, else null
 * @param shard "part/total" of a weights shard, or null
 * @param stem the model name an importance matrix was made for, which is still read for its family, or null
 */
record Auxiliary(
        ModelKind kind,
        @Nullable String sidecar,
        @Nullable String format,
        @Nullable String shard,
        @Nullable String stem) {}
