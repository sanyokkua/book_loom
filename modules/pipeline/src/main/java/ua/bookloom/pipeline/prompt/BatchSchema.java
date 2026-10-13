package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The batch draft's JSON response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchSchema {

    /**
     * One entry per item with its id and target. Deliberately flat — no {@code maxItems}, {@code maxLength} or
     * {@code additionalProperties} — because LM Studio never answered a structured reviewer call that carried such limits
     * on one model; the reply parser enforces the ids, so the schema need not. The optional {@code terms} object maps a
     * key term to the rendering used; its values are not typed here for the same reason, and the verifier checks them.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{\
            "id":{"type":"string"},"target":{"type":"string"},"terms":{"type":"object"}},"required":["id","target"]}}},"required":["items"]}
            """.strip();

    /**
     * The experimental notes arm (15i.X2): the same schema with an optional {@code notes} string written before each
     * {@code target}, which the code reads only to log. Optional, so a reply without notes still answers the schema.
     */
    public static final String SCHEMA_WITH_NOTES = """
            {"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{\
            "id":{"type":"string"},"notes":{"type":"string"},"target":{"type":"string"},"terms":{"type":"object"}},"required":["id","target"]}}},"required":["items"]}
            """.strip();
}
