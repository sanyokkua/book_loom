package ua.bookloom.api.project;

/** Hands out fresh identifiers, so logic that needs an id stays deterministic under test. */
@FunctionalInterface
public interface IdSource {

    /**
     * Returns a new identifier.
     *
     * @return a non-empty id never returned before by this source
     */
    String newId();
}
