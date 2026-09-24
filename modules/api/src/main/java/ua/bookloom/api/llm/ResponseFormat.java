package ua.bookloom.api.llm;

import java.util.Objects;

/** A requested structured response shape, expressed as a name and JSON schema string. */
public record ResponseFormat(String name, String jsonSchema) {

    /** Rejects a missing format name or schema. */
    public ResponseFormat {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
    }
}
