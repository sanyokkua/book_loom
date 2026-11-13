package ua.bookloom.llm.profile;

import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** The syntactic class of a quant token ("iquant", "kquant", "float"); it says how the token is spelt, not how good it is. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuantTypes {

    private static final Pattern K_QUANT = Pattern.compile("q\\d_k.*");
    private static final Pattern LEGACY = Pattern.compile("q\\d(?:_[01])?(?:_\\d_\\d)?");
    private static final Pattern AFFINE = Pattern.compile("\\dbit");
    private static final Set<String> FLOATS = Set.of("f32", "f16", "bf16", "fp8");
    private static final Set<String> MICROSCALING = Set.of("nvfp4", "mxfp4", "mxfp4_moe", "mxfp8");

    static @Nullable String of(final @Nullable String quant) {
        if (quant == null) {
            return null;
        }
        if (quant.startsWith("iq")) {
            return "iquant";
        }
        if (quant.startsWith("tq")) {
            return "ternary";
        }
        if (K_QUANT.matcher(quant).matches()) {
            return "kquant";
        }
        if (LEGACY.matcher(quant).matches()) {
            return "legacy";
        }
        return other(quant);
    }

    private static @Nullable String other(final String quant) {
        if (FLOATS.contains(quant)) {
            return "float";
        }
        if (MICROSCALING.contains(quant)) {
            return "microscaling";
        }
        if (AFFINE.matcher(quant).matches()) {
            return "mlx-affine";
        }
        return quant.startsWith("int") || quant.startsWith("w4") || quant.startsWith("w8") ? "integer" : null;
    }
}
