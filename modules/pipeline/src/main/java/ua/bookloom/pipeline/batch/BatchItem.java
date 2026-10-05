package ua.bookloom.pipeline.batch;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One segment of a batch as the model sees it.
 *
 * @param id the id the reply must answer under; letters, digits and {@code _ . : -} only, so it can sit inside an
 *     attribute or a JSON string unescaped
 * @param masked the masked source text, placeholder tokens included
 */
public record BatchItem(String id, String masked) {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.:-]+");

    /** Rejects a missing text and an id that is empty or holds a character that needs escaping. */
    public BatchItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(masked, "masked");
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a batch item id must be letters, digits and _ . : - only: " + id);
        }
    }
}
