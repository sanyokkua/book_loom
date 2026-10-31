package ua.bookloom.api.project;

import java.util.Objects;

/**
 * The default form of address between characters, for the two relations a book mostly holds.
 *
 * @param strangers the form used between people who do not know each other; non-null
 * @param family the form used inside a family; non-null
 */
public record Address(AddressForm strangers, AddressForm family) {

    /** Rejects a missing form. */
    public Address {
        Objects.requireNonNull(strangers, "strangers");
        Objects.requireNonNull(family, "family");
    }
}
