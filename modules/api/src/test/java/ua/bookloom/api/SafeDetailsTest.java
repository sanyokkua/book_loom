package ua.bookloom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ServerEngine;

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
            "reason",
            "httpStatus",
            "endpointHost",
            "modelName",
            "timeoutMillis",
            "attempt",
            "qaFindings",
            "expectedPlaceholders",
            "observedPlaceholders",
            "folderName",
            "promptTokens",
            "windowTokens",
            "engine");

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
                .withTokenCounts(9120, 8192)
                .render();

        assertThat(details)
                .isEqualTo("httpStatus=429, endpointHost=localhost:11434, model=llama3.1:8b, timeoutMs=30000, "
                        + "attempt=2/3, qaFindings=tagMismatch,lengthRatio, "
                        + "expectedPlaceholders=⟦g0⟧ ⟦g1⟧, observedPlaceholders=⟦g0⟧, "
                        + "promptTokens=9120, windowTokens=8192");
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

    // The window names the engine in its own language, so the details carry the engine and nothing a reply wrote.
    @Test
    void withEngine_constantName_isRenderedAndReadBack() {
        final String details = SafeDetails.empty()
                .withReason(FailureReason.ENGINE_MISMATCH)
                .withEndpoint(URI.create("http://127.0.0.1:11434"))
                .withEngine("LM_STUDIO")
                .render();

        assertThat(details).isEqualTo("reason=engine-mismatch, endpointHost=127.0.0.1:11434, engine=LM_STUDIO");
        assertThat(ServerEngine.in(details)).contains(ServerEngine.LM_STUDIO);
    }

    // The window says which id a server lacks in its own words, so the id is read back from the details.
    @ParameterizedTest
    @CsvSource({
        "'reason=model-not-listed, endpointHost=127.0.0.1:1234, model=gemma-4-e4b-typo, engine=LM_STUDIO', gemma-4-e4b-typo",
        "'model=google/gemma-4-e4b', google/gemma-4-e4b",
        "'httpStatus=404, model=a b, timeoutMs=5', a b"
    })
    void modelIn_renderedModel_isReadBack(final String details, final String model) {
        assertThat(SafeDetails.modelIn(details)).contains(model);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"reason=auth", "endpointHost=example.com"})
    void modelIn_noModel_isEmpty(final String details) {
        assertThat(SafeDetails.modelIn(details)).isEmpty();
    }

    // IF any text could be passed as the engine, THEN a reply's words could reach a dialog through it.
    @ParameterizedTest
    @ValueSource(strings = {"lm studio", "LM_STUDIO, model=x", "", "Ollama"})
    void withEngine_notAConstantName_isRefused(final String name) {
        final var target = SafeDetails.empty();
        assertThatThrownBy(() -> target.withEngine(name)).isInstanceOf(IllegalArgumentException.class);
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

    // The flag says "696 of 512 tokens" from these typed counts; the provider's sentence they came from never enters.
    @Test
    void withTokenCounts_696And512_rendersBothCounts() {
        final String details = SafeDetails.empty()
                .withReason(FailureReason.CONTEXT_OVERFLOW)
                .withTokenCounts(696, 512)
                .render();

        assertThat(details).isEqualTo("reason=context-overflow, promptTokens=696, windowTokens=512");
    }

    @Test
    void withTokenCounts_negativeCount_isRefused() {
        final var target = SafeDetails.empty();
        assertThatThrownBy(() -> target.withTokenCounts(-1, 512)).isInstanceOf(IllegalArgumentException.class);
    }

    // IF the folder's whole path were kept, THEN a dialog and a log line would name the person's home folder.
    @Test
    void withFolder_aDeepPath_keepsOnlyTheFoldersOwnName() {
        final String details = SafeDetails.empty()
                .withReason(FailureReason.DISK_FULL)
                .withFolder(Path.of("/Users/someone/Books"))
                .render();

        assertThat(details).isEqualTo("reason=disk-full, folder=Books");
    }

    // IF the reason could not be read back, THEN the interface could not word the advice in the display language.
    @Test
    void withReason_rendered_isReadBackByFailureReason() {
        final String details = SafeDetails.empty()
                .withReason(FailureReason.READ_ONLY)
                .withHttpStatus(500)
                .render();

        assertThat(FailureReason.in(details)).contains(FailureReason.READ_ONLY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"httpStatus=500", "reason=unheard-of", "folder=reason=disk-full"})
    void in_detailsWithoutAKnownLeadingReason_isEmpty(final String details) {
        assertThat(FailureReason.in(details)).isEmpty();
    }

    // The flag says "696 of 512 tokens" from the typed counts, never from the provider's sentence.
    @Test
    void promptTokensIn_renderedWithTokenCounts_readsBothBack() {
        final String details = SafeDetails.empty()
                .withReason(FailureReason.CONTEXT_OVERFLOW)
                .withHttpStatus(400)
                .withTokenCounts(696, 512)
                .render();

        assertThat(DetailCounts.promptTokensIn(details)).hasValue(696);
        assertThat(DetailCounts.windowTokensIn(details)).hasValue(512);
        assertThat(DetailCounts.promptTokensIn("reason=context-overflow, httpStatus=400"))
                .isEmpty();
        assertThat(DetailCounts.windowTokensIn(null)).isEmpty();
    }
}
