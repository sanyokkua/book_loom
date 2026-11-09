package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The batch draft's JSON response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchSchema {

    /**
     * The answer schema's name every translate-family call asks for; a refused setting is remembered by it, so it keeps
     * the batch draft's old name.
     */
    public static final String FORMAT_NAME = "draft-batch-json";

    /**
     * One entry per item with its id and target. Deliberately flat — no {@code maxItems}, {@code maxLength} or
     * {@code additionalProperties} — because LM Studio never answered a structured call that carried such limits
     * on one model; the reply parser enforces the ids, so the schema need not. The reply parser ignores any other field
     * an item carries.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{\
            "id":{"type":"string"},"target":{"type":"string"}},"required":["id","target"]}}},"required":["items"]}
            """.strip();
}
