package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.QuantTier;

/**
 * What the chip beside a model says.
 *
 * @param token the quant as written, such as {@code UD-Q4_K_XL}; non-blank
 * @param tier how the quant rates at the model's size, or null while the size is not known
 * @param fromFacts whether the server's own facts were read, so a later reading of the bare name never replaces it
 */
public record QuantChipModel(String token, @Nullable QuantTier tier, boolean fromFacts) {

    public QuantChipModel {
        Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }
}
