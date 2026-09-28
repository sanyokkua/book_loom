package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The reflect call's structured response schema requested from providers: a critique, never a rewrite. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReflectSchema {

    /** A response object with the candidate's issues, each a short note and a concrete suggestion. */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "issues":{"type":"array","items":{"type":"object","properties":{\
            "note":{"type":"string"},"suggestion":{"type":"string"}\
            },"required":["note","suggestion"],"additionalProperties":false}}\
            },"required":["issues"],"additionalProperties":false}
            """.strip();
}
