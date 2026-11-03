package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a pasted link, command or file name was read as, for the line under the Add a model id box.
 *
 * @param input the paste read once: the id to use, the repository, the file and the quant
 * @param modelLabel the person-facing model name ("GLM 4.7 Flash"); empty when the name gives no family
 * @param quantToken the quant as written in the paste ("UD-Q4_K_XL"), or null when none was named
 * @param pullCommand the {@code ollama pull} line to copy, or null when it does not apply; it is only ever shown
 */
public record PastedModel(
        ModelInput input,
        String modelLabel,
        @Nullable String quantToken,
        @Nullable String pullCommand) {

    public PastedModel {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(modelLabel, "modelLabel");
    }
}
