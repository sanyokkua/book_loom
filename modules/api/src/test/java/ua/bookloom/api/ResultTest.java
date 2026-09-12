package ua.bookloom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The {@code Result} envelope's invariants.
 *
 * <p>Shape tests: the envelope's shape is architecture — DD-14 and
 * {@code 02_Architecture/09_ERROR_HANDLING.md#result-envelope} — not a functional requirement.
 */
class ResultTest {

    private static final AppError FAILURE = AppError.of(ErrorCode.validation, "Invalid", "The input was rejected.");

    @Test
    void ok_value_carriesDataAndNoError() {
        final Result<String> result = Result.ok("book.epub");

        assertThat(result.isOk()).isTrue();
        assertThat(result.isErr()).isFalse();
        assertThat(result.data()).isEqualTo("book.epub");
        assertThat(result.error()).isNull();
    }

    @Test
    void err_appError_carriesErrorAndNoData() {
        final Result<String> result = Result.err(FAILURE);

        assertThat(result.isErr()).isTrue();
        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).isEqualTo(FAILURE);
        assertThat(result.data()).isNull();
    }

    // The "exactly one of data/error" invariant is what lets every caller treat isOk() as a complete answer, so both
    // ways of violating it are rejected at construction rather than discovered downstream.
    @Test
    void constructor_bothDataAndError_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Result<>("book.epub", FAILURE))
                .withMessageContaining("exactly one");
    }

    @Test
    void constructor_neitherDataNorError_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Result<>(null, null))
                .withMessageContaining("exactly one");
    }

    // Suppressed deliberately: NullAway forbids this call statically, which is exactly why the runtime guard still
    // has to exist and be proven. Every in-project caller is @NullMarked and so is checked at compile time, but the
    // requireNonNull is the boundary contract for anything that reaches this record without that analysis —
    // reflection, a future unmarked package, or a raw-typed call.
    @SuppressWarnings("NullAway")
    @Test
    void ok_null_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> Result.ok(null));
    }

    @Test
    void map_success_appliesTheMapper() {
        final Result<Integer> mapped = Result.ok("book.epub").map(String::length);

        assertThat(mapped.isOk()).isTrue();
        assertThat(mapped.data()).isEqualTo(9);
    }

    // Short-circuiting is the property that makes a chain safe to write without an isOk() check between each step,
    // so it is not enough that the error survives — the mapper must never run at all.
    @Test
    void map_failure_shortCircuitsWithoutInvokingTheMapper() {
        final AtomicBoolean invoked = new AtomicBoolean(false);

        final Result<Integer> mapped = Result.<String>err(FAILURE).map(value -> {
            invoked.set(true);
            return value.length();
        });

        assertThat(invoked)
                .as("the mapper must not see a value that does not exist")
                .isFalse();
        assertThat(mapped.isErr()).isTrue();
        assertThat(mapped.error()).isEqualTo(FAILURE);
    }

    @Test
    void flatMap_success_appliesTheMapperAndUnwrapsOneLevel() {
        final Result<Integer> mapped = Result.ok("book.epub").flatMap(value -> Result.ok(value.length()));

        assertThat(mapped.isOk()).isTrue();
        assertThat(mapped.data()).isEqualTo(9);
    }

    @Test
    void flatMap_mapperFails_propagatesTheMappersError() {
        final Result<Integer> mapped = Result.ok("book.epub").flatMap(value -> Result.err(FAILURE));

        assertThat(mapped.isErr()).isTrue();
        assertThat(mapped.error()).isEqualTo(FAILURE);
    }

    @Test
    void flatMap_failure_shortCircuitsWithoutInvokingTheMapper() {
        final AtomicBoolean invoked = new AtomicBoolean(false);

        final Result<Integer> mapped = Result.<String>err(FAILURE).flatMap(value -> {
            invoked.set(true);
            return Result.ok(value.length());
        });

        assertThat(invoked).isFalse();
        assertThat(mapped.error()).isEqualTo(FAILURE);
    }
}
