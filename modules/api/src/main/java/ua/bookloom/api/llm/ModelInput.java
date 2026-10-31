package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the person pasted, read once and the same way every time; nothing was opened, run or sent to read it.
 *
 * @param raw the text as pasted, trimmed
 * @param form the shape it was recognised as
 * @param modelId the id to use with the provider; non-blank
 * @param repo the Hugging Face repository {@code org/name}, or null when the paste named none
 * @param file the model file name, or null when the paste named none
 * @param quant the quant token lower-cased without the dynamic prefix ("q4_k_xl"), or null when none was named
 * @param dynamic true when the quant carried Unsloth's dynamic prefix ("UD-")
 */
public record ModelInput(
        String raw,
        ModelInputForm form,
        String modelId,
        @Nullable String repo,
        @Nullable String file,
        @Nullable String quant,
        boolean dynamic) {

    public ModelInput {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(modelId, "modelId");
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
    }
}
