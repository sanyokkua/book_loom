package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the program says about a quant label.
 *
 * @param token the label as read, such as "UD-Q4_K_XL"
 * @param baseType the single base type the label rests on ("Q4_K"), or null when it names none
 * @param format the container format when the label shows one ("GGUF"), or null
 * @param dynamic whether the label marks a dynamic-precision build
 * @param imatrix whether the label marks an importance-matrix build
 * @param qat whether the label marks quantization-aware training
 * @param bitClass the nominal bit width class ("4-bit"), or null when unknown
 * @param sizeClass the size band the tier was judged for, or null when the model's size is unknown
 * @param tier the tier for that band, or null when the table has no row
 * @param source a short text naming where the advice comes from
 */
public record QuantAdvice(
        String token,
        @Nullable String baseType,
        @Nullable String format,
        boolean dynamic,
        boolean imatrix,
        boolean qat,
        @Nullable String bitClass,
        @Nullable SizeClass sizeClass,
        @Nullable QuantTier tier,
        String source) {

    public QuantAdvice {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(source, "source");
    }
}
