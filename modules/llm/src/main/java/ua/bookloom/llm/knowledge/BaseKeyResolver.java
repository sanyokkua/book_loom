package ua.bookloom.llm.knowledge;

import java.util.Optional;
import ua.bookloom.api.llm.ModelIdentity;

/** Names the model a fine-tune is built on, as a row key to look up. */
@FunctionalInterface
public interface BaseKeyResolver {

    /** Reads the identity's own {@code baseFamily} as it stands. */
    BaseKeyResolver IDENTITY_BASE_FAMILY = id -> Optional.ofNullable(id.baseFamily());

    /**
     * The base model's key.
     *
     * @param id the model
     * @return the key, or empty when the model is not a known fine-tune
     */
    Optional<String> baseKeyOf(ModelIdentity id);
}
