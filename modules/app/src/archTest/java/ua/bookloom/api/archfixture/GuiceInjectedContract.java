package ua.bookloom.api.archfixture;

import com.google.inject.Inject;

/**
 * Violation fixture for {@code api-is-framework-free}: a type in an {@code :api} package carrying a Guice
 * annotation. The impl modules legitimately hold Guice; {@code :api} may not, because a framework admitted at the
 * dependency floor propagates to all eight modules.
 */
public final class GuiceInjectedContract {

    private final String value;

    @Inject
    public GuiceInjectedContract(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
