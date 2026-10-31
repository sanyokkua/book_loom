package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;

/**
 * Who a model is, as its GGUF header says.
 *
 * @param architecture the {@code general.architecture} in its canonical spelling, or null when the header has none
 * @param baseModels the models listed under {@code general.base_model.N.name}, in header order; never null, may be
 *     empty (a model with no stated base)
 */
public record GgufIdentity(@Nullable String architecture, List<ModelIdentity> baseModels) {

    public GgufIdentity {
        Objects.requireNonNull(baseModels, "baseModels");
        baseModels = List.copyOf(baseModels);
    }
}
