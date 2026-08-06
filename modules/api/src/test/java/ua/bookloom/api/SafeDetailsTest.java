package ua.bookloom.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The safe-details allowlist — the privacy half of the error envelope.
 *
 * <p>These tests half-cover {@code FR-NOTIF-04}. This change builds the allowlist and proves nothing outside it can
 * be expressed; the other half — surfacing the resulting {@code AppError} to the user through a toast and an
 * expandable panel — arrives with the notification UI in Stage D, and is not asserted here.
 */
class SafeDetailsTest {

    /** The allowlist, written out rather than read from the type, so the type cannot define its own correctness. */
    private static final List<String> ALLOWLIST =
            List.of("httpStatus", "endpointHost", "modelName", "timeoutMillis", "attempt", "qaFindings");

    // Covers: FR-NOTIF-04 (partly — construction only) — WHEN a failure carries technical detail, the system SHALL
    // populate AppError.details only from the allowlisted fields: HTTP status, endpoint host, model name, timeout,
    // attempt count and QA finding names.
    @Test
    void render_everyAllowlistedField_rendersAllOfThem() {
        final String details = SafeDetails.empty()
                .withHttpStatus(429)
                .withEndpoint(URI.create("http://localhost:11434/api/chat"))
                .withModelName("llama3.1:8b")
                .withTimeout(Duration.ofSeconds(30))
                .withAttempt(2, 3)
                .withQaFindings(List.of("tagMismatch", "lengthRatio"))
                .render();

        assertThat(details)
                .isEqualTo("httpStatus=429, endpointHost=localhost, model=llama3.1:8b, timeoutMs=30000, "
                        + "attempt=2/3, qaFindings=tagMismatch,lengthRatio");
    }

    // Covers: FR-NOTIF-04 (partly — construction only) — IF a value would carry a credential, THEN the system SHALL
    // NOT place it in AppError.details: only the endpoint's host is kept, never the user-info or query string that
    // carries a token.
    @Test
    void withEndpoint_uriCarryingCredentialsAndQuery_keepsOnlyTheHost() {
        final String details = SafeDetails.empty()
                .withEndpoint(URI.create("https://user:sk-secret-token@api.example.com/v1/chat?api_key=sk-another"))
                .render();

        assertThat(details).isEqualTo("endpointHost=api.example.com");
        assertThat(details).doesNotContain("sk-secret-token", "sk-another", "user");
    }

    /**
     * The structural half of the privacy guarantee, and the assertion that must not be deleted.
     *
     * <p>Everything else here tests what the type does with a value. This tests what it <em>cannot be asked to
     * do</em>. The allowlist is the record's component set, so a new field cannot be smuggled in as an
     * implementation detail — it would change this list — and no method anywhere accepts a {@link Throwable}, so
     * {@code withMessage(e.getMessage())} and {@code withBody(response)} are not merely discouraged, they do not
     * exist to be called.
     */
    // Covers: FR-NOTIF-04 (partly — construction only) — The system SHALL NOT admit an arbitrary exception message
    // or response body into AppError.details; only allowlisted typed fields have an entry point.
    @Test
    void type_componentsAndMethods_admitNothingOutsideTheAllowlist() {
        assertThat(SafeDetails.class.getRecordComponents())
                .as("the component set IS the allowlist; adding one widens what reaches a log file and a dialog")
                .extracting(RecordComponent::getName)
                .containsExactlyElementsOf(ALLOWLIST);

        final List<Method> publicMethods = Arrays.stream(SafeDetails.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .toList();

        assertThat(publicMethods)
                .as("no allowlisted field may be populated from a throwable")
                .allSatisfy(method ->
                        assertThat(Stream.of(method.getParameterTypes())).noneMatch(Throwable.class::isAssignableFrom));
    }

    // A value passed to the wrong field cannot be prevented by an API, but its blast radius can be bounded: a newline
    // would otherwise split one log line into two and let a stack trace masquerade as further log entries.
    @Test
    void withModelName_multilineValue_isFlattenedToOneLine() {
        final String details = SafeDetails.empty()
                .withModelName("llama3\njava.lang.IllegalStateException: boom\n\tat ua.bookloom.Foo.bar(Foo.java:1)")
                .render();

        assertThat(details).doesNotContain("\n", "\r", "\t");
    }

    @Test
    void withModelName_overlongValue_isTruncated() {
        final String details =
                SafeDetails.empty().withModelName("m".repeat(500)).render();

        assertThat(details).hasSizeLessThan(200);
        assertThat(details).endsWith("…");
    }

    @Test
    void withQaFindings_moreThanTheRenderLimit_summarisesTheRemainder() {
        final List<String> findings =
                IntStream.range(0, 14).mapToObj(index -> "finding" + index).toList();

        final String details = SafeDetails.empty().withQaFindings(findings).render();

        assertThat(details).contains("finding0").contains("+4 more").doesNotContain("finding10");
    }

    @Test
    void render_noFieldsSet_returnsNullRatherThanAnEmptyString() {
        assertThat(SafeDetails.empty().render()).isNull();
    }

    @Test
    void withQaFindings_emptyList_addsNoField() {
        assertThat(SafeDetails.empty().withQaFindings(List.of()).render()).isNull();
    }

    // Immutability is what makes a partially-built value safe to share: one caller adding a field must not change
    // what another caller already holds.
    @Test
    void withHttpStatus_appliedToAnExistingValue_leavesTheOriginalUnchanged() {
        final SafeDetails base = SafeDetails.empty().withModelName("llama3");

        final SafeDetails extended = base.withHttpStatus(500);

        assertThat(base.render()).isEqualTo("model=llama3");
        assertThat(extended.render()).isEqualTo("httpStatus=500, model=llama3");
    }
}
