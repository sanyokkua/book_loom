package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/**
 * {@link DocumentService#unmask}'s placeholder-multiset hard gate (task group 7.9, 7.10), driven through the port
 * against hand-built segments — every failure mode the gate must reject, the two shapes it must accept, and the
 * report a failure carries. Restoring a passing target is {@link UnmaskRestoreTest}'s concern, not this class's;
 * every failing case here supplies an empty placeholder map because the gate rejects before restore ever runs.
 */
class UnmaskPlaceholderGateTest {

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_droppedToken_returnsValidationError() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧ door", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "⟦g0⟧старі двері");

        assertThat(result.isErr()).isTrue();
        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_duplicatedToken_returnsValidationError() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "⟦g0⟧старі⟦g1⟧⟦g1⟧");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_inventedToken_returnsValidationError() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "⟦g0⟧старі⟦g1⟧ ⟦g7⟧");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_twoDigitTokenReplacedByItsOneDigitPrefix_returnsValidationError() {
        final String masked = "⟦g0⟧a⟦g1⟧b⟦g2⟧c⟦g3⟧d⟦g4⟧e⟦g5⟧f⟦g6⟧g⟦g7⟧h⟦g8⟧i⟦g9⟧j⟦g10⟧k⟦g11⟧l⟦g12⟧";
        final String target = "⟦g0⟧a⟦g1⟧b⟦g2⟧c⟦g3⟧d⟦g4⟧e⟦g5⟧f⟦g6⟧g⟦g7⟧h⟦g8⟧i⟦g9⟧j⟦g10⟧k⟦g11⟧l⟦g1⟧";
        final Segment segment = segment(masked, new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, target);

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_targetInventsATokenForASourceWithNone_returnsValidationError() {
        final Segment segment = segment("Plain prose.", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Звичайна ⟦g0⟧ проза.");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_emptyTargetForASegmentThatHadTokens_returnsValidationError() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_reorderedTokens_passesTheGate() {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "<b>");
        placeholders.put("g1", "</b>");
        placeholders.put("g2", "<i>");
        placeholders.put("g3", "</i>");
        final Segment segment = segment("⟦g0⟧A⟦g1⟧ and ⟦g2⟧B⟦g3⟧", placeholders);

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "⟦g2⟧Б⟦g3⟧ і ⟦g0⟧А⟦g1⟧");

        assertThat(result.isOk()).isTrue();
        assertThat(result.error()).isNull();
    }

    // IF the placeholder multiset of the target differs from the source, THEN
    // the chunk fails as a validation error with no repair attempt.
    @Test
    void unmask_segmentWithNoPlaceholders_acceptsATargetWithNone() {
        final Segment segment = segment("Plain prose.", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "Звичайна проза.");

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).isEqualTo("Звичайна проза.");
    }

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, SHALL bound the model-derived observed multiset by token count and
    // per-token length and state what it omitted, and SHALL carry no book text.
    @Test
    void unmask_dropOneOfTwoTokens_namesTheMissingToken() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "⟦g0⟧старі");

        final AppError error = errorOf(result);
        assertThat(error.details()).isEqualTo("expectedPlaceholders=⟦g0⟧ ⟦g1⟧, observedPlaceholders=⟦g0⟧");
    }

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, SHALL bound the model-derived observed multiset by token count and
    // per-token length and state what it omitted, and SHALL carry no book text.
    @Test
    void unmask_fortyPlaceholderSegment_reportIsNotTruncated() {
        final Segment segment = segment(fortyTokens(), new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "⟦g0⟧");

        final AppError error = errorOf(result);
        assertThat(error.details())
                .isEqualTo("expectedPlaceholders=" + fortyTokensSpaceJoined() + ", observedPlaceholders=⟦g0⟧");
    }

    // WHEN the multiset comparison fails, the system SHALL name every token of the expected
    // multiset without truncating it, SHALL bound the model-derived observed multiset by token count and
    // per-token length and state what it omitted, and SHALL carry no book text.
    @Test
    void unmask_targetWithNoPlaceholders_detailCarriesNoBookText() {
        final Segment segment = segment("⟦g0⟧old⟦g1⟧ door", new LinkedHashMap<>());

        final Result<String> result = newService().unmask(BookFormat.EPUB, segment, "старі двері");

        final AppError error = errorOf(result);
        assertThat(error.details()).isEqualTo("expectedPlaceholders=⟦g0⟧ ⟦g1⟧, observedPlaceholders=");
    }

    /** Every whole-grammar token from {@code ⟦g0⟧} through {@code ⟦g39⟧}, concatenated with no separator. */
    private static String fortyTokens() {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            builder.append("⟦g").append(index).append("⟧");
        }
        return builder.toString();
    }

    /** {@link #fortyTokens()}, space-separated the way {@code SafeDetails} renders a token list. */
    private static String fortyTokensSpaceJoined() {
        return fortyTokens().replace("⟧⟦", "⟧ ⟦");
    }

    /** A minimal EPUB-kind segment carrying {@code masked} and {@code placeholders}; every other field is a stand-in. */
    private static Segment segment(String masked, Map<String, String> placeholders) {
        return new Segment(
                "seg-1",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                placeholders,
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
