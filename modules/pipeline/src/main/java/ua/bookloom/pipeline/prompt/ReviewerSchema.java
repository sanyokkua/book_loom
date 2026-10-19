package ua.bookloom.pipeline.prompt;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.reviewer.ReviewCriterion;
import ua.bookloom.pipeline.reviewer.ReviewStatus;

/** The reviewer's structured response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReviewerSchema {

    /**
     * One result per pair with its id, status and, by status, its edits or its rewrite. Deliberately flat — no
     * {@code maxItems}, {@code maxLength} or {@code additionalProperties} — because LM Studio never answered a
     * structured call that carried such limits on one model; only the closed vocabularies are enumerated. The reply
     * reader enforces the ids and checks every edit, so the schema need not.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{"results":{"type":"array","items":{"type":"object","properties":{\
            "id":{"type":"string"},"status":{"type":"string","enum":%s},\
            "edits":{"type":"array","items":{"type":"object","properties":{\
            "criterion":{"type":"string","enum":%s},"quote":{"type":"string"},"replacement":{"type":"string"}},\
            "required":["criterion","quote","replacement"]}},\
            "rewrite":{"type":"string"}},"required":["id","status"]}}},"required":["results"]}
            """.formatted(
            quoted(Arrays.stream(ReviewStatus.values()).map(ReviewStatus::wire)),
            quoted(Arrays.stream(ReviewCriterion.values()).map(ReviewCriterion::wire))).strip();

    private static String quoted(final Stream<String> words) {
        return words.map(word -> "\"" + word + "\"").collect(Collectors.joining(",", "[", "]"));
    }
}
