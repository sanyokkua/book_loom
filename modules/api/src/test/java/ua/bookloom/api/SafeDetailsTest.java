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
    private static final List<String> ALLOWLIST = List.of(
            "httpStatus",
            "endpointHost",
            "modelName",
            "timeoutMillis",
            "attempt",
            "qaFindings",
            "expectedPlaceholders",
            "observedPlaceholders");

    // WHEN a failure carries technical detail, the system SHALL
    // populate AppError.details only from the allowlisted fields: HTTP status, endpoint host, model name, timeout,
    // attempt count, QA finding names and the expected/observed placeholder multisets (ADR-0031).
    @Test
    void render_everyAllowlistedField_rendersAllOfThem() {
        final String details = SafeDetails.empty()
                .withHttpStatus(429)
                .withEndpoint(URI.create("http://localhost:11434/api/chat"))
                .withModelName("llama3.1:8b")
                .withTimeout(Duration.ofSeconds(30))
                .withAttempt(2, 3)
                .withQaFindings(List.of("tagMismatch", "lengthRatio"))
                .withPlaceholderMultiset(List.of("⟦g0⟧", "⟦g1⟧"), List.of("⟦g0⟧"))
                .render();

        assertThat(details)
                .isEqualTo("httpStatus=429, endpointHost=localhost, model=llama3.1:8b, timeoutMs=30000, "
                        + "attempt=2/3, qaFindings=tagMismatch,lengthRatio, "
                        + "expectedPlaceholders=⟦g0⟧ ⟦g1⟧, observedPlaceholders=⟦g0⟧");
    }

    // IF a value would carry a credential, THEN the system SHALL
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
    // The system SHALL NOT admit an arbitrary exception message
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

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, SHALL bound the model-derived observed multiset by token count and
    // per-token length and state what it omitted, and SHALL carry no book text.
    @Test
    void withPlaceholderMultiset_fortyExpectedTokens_rendersEveryTokenUntruncated() {
        final List<String> expected =
                IntStream.range(0, 40).mapToObj(index -> "⟦g" + index + "⟧").toList();

        final String details = SafeDetails.empty()
                .withPlaceholderMultiset(expected, List.of("⟦g0⟧"))
                .render();

        assertThat(details).contains("⟦g39⟧");
        assertThat(details).doesNotContain("…");
    }

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, SHALL bound the model-derived observed multiset by token count and
    // per-token length and state what it omitted, and SHALL carry no book text.
    @Test
    void withPlaceholderMultiset_emptyObservedAgainstNonEmptyExpected_stillRendersAnEmptyObservedField() {
        final String details = SafeDetails.empty()
                .withPlaceholderMultiset(List.of("⟦g0⟧", "⟦g1⟧"), List.of())
                .render();

        assertThat(details).isEqualTo("expectedPlaceholders=⟦g0⟧ ⟦g1⟧, observedPlaceholders=");
    }

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, SHALL bound the model-derived observed multiset by token count and
    // per-token length and state what it omitted, and SHALL carry no book text.
    @Test
    void withPlaceholderMultiset_bothListsEmpty_addsNoField() {
        assertThat(SafeDetails.empty()
                        .withPlaceholderMultiset(List.of(), List.of())
                        .render())
                .isNull();
    }

    // WHEN the multiset comparison fails, the system SHALL render the observed tokens in a
    // form a caller can read back token by token — bounded, because that side is provider output rather than this
    // system's own.
    // The token grammar's digit run is unbounded, so one degenerate token measured 200,003 characters and rendered
    // a 200-KB error dialog and a 200-KB log line to match. Per-token length is capped and the cut is marked.
    @Test
    void withPlaceholderMultiset_singleOverlongObservedToken_isCappedWithAnEllipsis() {
        final String details = SafeDetails.empty()
                .withPlaceholderMultiset(List.of("⟦g0⟧"), List.of("⟦g123456789012345678901234567890⟧"))
                .render();

        assertThat(details).isEqualTo("expectedPlaceholders=⟦g0⟧, observedPlaceholders=⟦g12345678901234…");
    }

    // WHEN the multiset comparison fails, the system SHALL render the observed tokens in a
    // form a caller can read back token by token — bounded, because that side is provider output rather than this
    // system's own.
    // Volume is the other half of the same unboundedness: 50,000 observed tokens rendered 438,937 characters. The
    // overflow is stated rather than dropped in silence, so a reader can tell a bounded render from a short list.
    @Test
    void withPlaceholderMultiset_moreObservedTokensThanTheCap_statesTheOverflow() {
        final List<String> observed =
                IntStream.range(0, 200).mapToObj(index -> "⟦g" + index + "⟧").toList();

        final String details = SafeDetails.empty()
                .withPlaceholderMultiset(List.of("⟦g0⟧"), observed)
                .render();

        assertThat(details).contains("⟦g63⟧").contains("+136 more").doesNotContain("⟦g64⟧");
    }

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, however the observed side is bounded.
    // The two sides have different origins and must keep different treatment: the repair tier asks the model to
    // restore exactly the expected list, so bounding that side would produce a prompt that asks for part of the
    // formatting and silently drops the rest.
    @Test
    void withPlaceholderMultiset_boundedObservedSide_leavesTheExpectedSideInFull() {
        final List<String> expected =
                IntStream.range(0, 40).mapToObj(index -> "⟦g" + index + "⟧").toList();
        final List<String> observed =
                IntStream.range(0, 200).mapToObj(index -> "⟦g" + index + "⟧").toList();

        final String details =
                SafeDetails.empty().withPlaceholderMultiset(expected, observed).render();

        assertThat(details).startsWith("expectedPlaceholders=⟦g0⟧ ⟦g1⟧ ⟦g2⟧ ");
        assertThat(details).contains("⟦g39⟧, observedPlaceholders=");
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
