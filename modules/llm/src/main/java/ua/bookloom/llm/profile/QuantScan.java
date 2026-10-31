package ua.bookloom.llm.profile;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The quant tokens found in a name and what is left of it.
 *
 * @param quant the first quant lower-cased without "ud-" ("q4_k_xl"), or null when the name has none
 * @param label the first quant as written, with its "UD-" prefix, or null
 * @param dynamic true when the first quant had the "ud-" prefix
 * @param extra the later quants in the same normal form; never null, may be empty
 * @param rest the lower-case name with every quant token replaced by "-"
 */
record QuantScan(@Nullable String quant, @Nullable String label, boolean dynamic, List<String> extra, String rest) {

    QuantScan {
        Objects.requireNonNull(extra, "extra");
        Objects.requireNonNull(rest, "rest");
    }
}
