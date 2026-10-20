package ua.bookloom.pipeline;

import java.util.UUID;
import ua.bookloom.api.project.IdSource;

/** The production {@link IdSource}: random UUIDs. */
public final class UuidIdSource implements IdSource {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
