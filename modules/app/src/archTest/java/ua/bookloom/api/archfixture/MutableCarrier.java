package ua.bookloom.api.archfixture;

/**
 * Violation fixture for {@code records-first}: a mutable class where a carrier package requires a record. This is
 * the shape DD-05 forbids — aliasable state crossing a module boundary that the receiver cannot trust.
 */
public final class MutableCarrier {

    private String title = "";

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
