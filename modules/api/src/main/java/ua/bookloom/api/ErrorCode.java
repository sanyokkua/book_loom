package ua.bookloom.api;

/**
 * The typed failure classification carried by every {@link AppError}.
 *
 * <p>One closed set for the whole application: callers branch on the code, never on a message string or an HTTP
 * status. The constants are lowercase because they are a vocabulary fixed verbatim by
 * {@code docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code}, not ordinary Java constants.
 *
 * <p>Most of these cannot be produced yet. Ten of the fifteen describe failures of I/O that does not exist until the
 * provider client lands; they ship now because seam F2 is a <em>contract</em>, and a contract that grows a constant
 * whenever a producer appears is a moving target for every change that compiles against it. Being currently
 * unreachable is not a defect in an enum constant.
 *
 * <p>{@link #isRetryable()} is the single source of truth for {@link AppError#retryable()}. The record component
 * exists because the specification fixes the envelope's shape, but its value is derived here rather than chosen per
 * call site, so retry policy cannot drift between the layer that classifies a failure and the layer that reacts to
 * it.
 */
public enum ErrorCode {

    /** HTTP 401/403 — the endpoint rejected the credential. */
    auth(false),

    /** A connect or read timeout elapsed. Retrying with a fresh deadline is meaningful. */
    timeout(true),

    /** HTTP 429 — the endpoint is rate limiting; honour {@code Retry-After}. */
    rateLimited(true),

    /** Connection refused or no route to host — nothing is listening. */
    unreachable(true),

    /** The endpoint does not know the requested model. */
    modelNotFound(false),

    /**
     * A model-list or capability-discovery call failed for a reason other than auth or unreachability — a malformed
     * or unparseable listing. Distinct from {@link #modelNotFound} (a specific model is absent) and from
     * {@link #modelUnavailable} (a <em>bound</em> model is missing at run time).
     */
    discoveryFailed(true),

    /** A model the project is bound to is not offered by the provider at run or resume time. */
    modelUnavailable(false),

    /** HTTP 5xx — the endpoint failed internally. */
    upstream(true),

    /** A credential reference resolves to nothing: the env var or keychain entry is absent. */
    missingCredential(false),

    /** The prompt exceeds the model's context window. */
    contextWindow(false),

    /** HTTP 200 with no usable content — the model emitted nothing at all before sanitization. */
    emptyCompletion(false),

    /** The user cancelled the job. Not a failure of the software. */
    cancelled(false),

    /** Input or configuration is invalid, including a QA hard-gate failure. */
    validation(false),

    /** An unexpected throwable, wrapped at a module boundary. */
    internal(false),

    /**
     * An exclusive resource is already held, so the request cannot proceed now.
     *
     * <p>Its defining case is the single-flight inference gate — the local model is serving another request — and
     * it carries the same meaning for the process single-instance lock: another copy of the application already
     * holds it. Deliberately not retryable in either case; the caller is told to try again later rather than
     * queueing behind the holder.
     *
     * <p>The specification's inline note describes this constant in terms of the inference gate alone, so the
     * second use is a deliberate widening rather than an oversight — recorded in <strong>ADR-0022</strong>, which
     * also explains why a sixteenth constant was the wrong answer.
     */
    busy(false);

    private final boolean retryable;

    ErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * Whether a failure of this kind may succeed if the same call is made again.
     *
     * <p>Retry is service-owned and keyed on this flag: a non-retryable code fails immediately rather than consuming
     * an attempt budget. {@link #busy} is deliberately <em>not</em> retryable — the caller is told to try again
     * later, it does not queue behind the held gate.
     *
     * @return {@code true} when a fresh attempt is worth making
     */
    public boolean isRetryable() {
        return retryable;
    }
}
