package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.AppError;

/**
 * What the dashboard banner says instead of the plain run-state banner: a provider failure, a refusal in place, or the
 * name of the input a start was missing.
 *
 * <p>The failure a notice carries is shown by its own message, passed through as data; the cause and the details never
 * reach a notice.
 */
public sealed interface RunNotice {

    /**
     * The run's own provider-error state: names the code and offers the provider settings.
     *
     * @param error the failure that ended the run or its preparation; never {@code null}
     */
    record ProviderError(AppError error) implements RunNotice {
        public ProviderError {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * A failure reported in place on the screen that refused it, with no route to the settings.
     *
     * @param error the refusal; never {@code null}
     */
    record Refused(AppError error) implements RunNotice {
        public Refused {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * A start that was refused because an input it needs is missing.
     *
     * @param which the first missing input, in the order a person supplies them; never {@code null}
     */
    record MissingInput(Input which) implements RunNotice {
        public MissingInput {
            Objects.requireNonNull(which, "which");
        }
    }

    /**
     * An input a start needs. There is no target-language constant: the brief defaults the language and accepts only
     * the supported ones, so a start can never be missing it.
     */
    enum Input {
        /** No book is open. */
        BOOK("book"),
        /** No model is chosen. */
        MODEL("model");

        private final String token;

        Input(final String token) {
            this.token = token;
        }

        /**
         * The word the catalogue selects this input's wording by.
         *
         * @return a non-blank lower-case token
         */
        public String token() {
            return token;
        }
    }
}
