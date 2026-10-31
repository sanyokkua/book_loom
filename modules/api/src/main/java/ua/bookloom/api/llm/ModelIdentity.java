package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Who a model is, as far as its name says; the quant, format and publisher are not part of it.
 *
 * @param family the lower-case family words joined by "-", or null when the name has none
 * @param version the generation as written ("3.5", "3n"), or null
 * @param line the named line inside a generation ("nano", "lightning"), or null
 * @param sizeB total parameters in billions, or null when the name does not give them
 * @param effB the effective size of an "e4b" style name in billions, or null; never a total size
 * @param activeB the active parameters of a mixture-of-experts name in billions, or null
 * @param variants sorted variant words such as "instruct" or "thinking"; never null, may be empty
 * @param kind what the name stands for
 * @param baseFamily the family a fine-tune is built on, or null when unknown
 * @param distillOf the family a distillation learned from, or null
 * @param quantLabel the quant as written in the name ("UD-Q4_K_XL"), or null when the name has none
 * @param needsShow the reasons the name alone is not enough and the engine must be asked; never null, may be empty
 */
public record ModelIdentity(
        @Nullable String family,
        @Nullable String version,
        @Nullable String line,
        @Nullable Double sizeB,
        @Nullable Double effB,
        @Nullable Double activeB,
        List<String> variants,
        ModelKind kind,
        @Nullable String baseFamily,
        @Nullable String distillOf,
        @Nullable String quantLabel,
        List<String> needsShow) {

    public ModelIdentity {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(needsShow, "needsShow");
        variants = List.copyOf(variants);
        needsShow = List.copyOf(needsShow);
    }
}
