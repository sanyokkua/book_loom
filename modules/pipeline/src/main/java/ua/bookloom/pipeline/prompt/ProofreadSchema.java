package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The proofread call's reply schema (design D15 "Reply"): {@code {"unchanged":true}} for a window that needs no change,
 * else the changed items alone, so a proofreader that finds nothing wrong says so in one word. Its {@code title}
 * repeats the format name, because an Ollama request carries the schema alone as {@code format}, so the name shows on
 * both dialects' wire. Neither field is required: the reply reader decides which one the answer holds.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProofreadSchema {

    /** The response-format name, the pseudo model's switch and the schema's title. */
    public static final String FORMAT_NAME = "proofread";

    /** Either the unchanged flag or the changed items, each with its segment id and new target. */
    public static final String SCHEMA = """
            {"title":"proofread","type":"object","properties":{\
            "unchanged":{"type":"boolean"},\
            "items":{"type":"array","items":{"type":"object","properties":{\
            "id":{"type":"string"},"target":{"type":"string"}},"required":["id","target"]}}\
            }}
            """.strip();
}
