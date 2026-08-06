package ua.bookloom.api;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The uniform success-or-failure envelope returned by every port method in {@code :api}.
 *
 * <p>Seam F2. No checked exception crosses a module boundary: a boundary catches, classifies, and returns
 * {@code Result.err(...)} instead. Callers branch on {@link #isOk()} rather than catching.
 *
 * <p>It is a record with two nullable components rather than a sealed {@code Ok}/{@code Err} pair because
 * {@code docs/specification/02_Architecture/09_ERROR_HANDLING.md#result-envelope} fixes that form. The invariant a
 * sealed pair would give structurally — exactly one side present — is enforced in the compact constructor instead,
 * so it holds for every instance however it was built.
 *
 * @param <T> the success value's type
 * @param data the success value, or {@code null} when this is a failure
 * @param error the failure, or {@code null} when this is a success
 */
public record Result<T>(@Nullable T data, @Nullable AppError error) {

    /**
     * Enforces that exactly one of {@code data} and {@code error} is present.
     *
     * <p>Both present would mean a caller could read a value from a failed operation; neither present would mean a
     * success carrying nothing, which {@link #map} could not then propagate. Rejecting both cases here is what lets
     * every consumer treat {@code isOk()} as a complete answer.
     */
    public Result {
        if ((data == null) == (error == null)) {
            throw new IllegalArgumentException("exactly one of data/error must be non-null, but both were "
                    + (data == null ? "absent" : "present"));
        }
    }

    /**
     * Wraps a success value.
     *
     * @param data the value, never {@code null}
     * @param <T> the value's type
     * @return a successful result
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(Objects.requireNonNull(data, "data"), null);
    }

    /**
     * Wraps a failure.
     *
     * @param error the failure, never {@code null}
     * @param <T> the value type this result would have carried
     * @return a failed result
     */
    public static <T> Result<T> err(AppError error) {
        return new Result<>(null, Objects.requireNonNull(error, "error"));
    }

    /**
     * Whether this result carries a value.
     *
     * @return {@code true} when {@link #error()} is absent
     */
    public boolean isOk() {
        return error == null;
    }

    /**
     * Whether this result carries a failure.
     *
     * @return {@code true} when {@link #error()} is present
     */
    public boolean isErr() {
        return error != null;
    }

    /**
     * Transforms the value, short-circuiting on failure.
     *
     * <p>The mapper is not invoked at all when this result is a failure — the existing error passes through
     * unchanged, which is what makes a chain of transformations safe to write without an {@code isOk()} check
     * between each step.
     *
     * @param mapper the transformation, applied only on success
     * @param <R> the transformed value's type
     * @return the transformed success, or this failure
     */
    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        final AppError currentError = error;
        if (currentError != null) {
            return err(currentError);
        }
        return ok(mapper.apply(Objects.requireNonNull(data)));
    }

    /**
     * Transforms the value into another result, short-circuiting on failure.
     *
     * <p>As {@link #map}, but for a transformation that can itself fail — its failure becomes this chain's failure
     * rather than nesting inside a successful result.
     *
     * @param mapper the transformation, applied only on success
     * @param <R> the transformed value's type
     * @return the mapper's result, or this failure
     */
    public <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        final AppError currentError = error;
        if (currentError != null) {
            return err(currentError);
        }
        return Objects.requireNonNull(mapper.apply(Objects.requireNonNull(data)), "mapper result");
    }
}
