package ua.bookloom.llm.profile;

import java.util.Objects;
import ua.bookloom.api.llm.ModelIdentity;

/**
 * One parsed name.
 *
 * @param identity who the model is
 * @param facts how the name says it is published and stored
 * @param origin where the name came from: the paste form, the repository, the file and the publisher
 */
public record ParsedName(ModelIdentity identity, NameFacts facts, NameOrigin origin) {

    public ParsedName {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(origin, "origin");
    }
}
