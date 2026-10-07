package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The strict response schema of the recurring-term choice: one keep-or-drop verdict per candidate. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TermChoiceSchema {

    /** An object holding the verdict list. */
    public static final String SCHEMA = """
            {"type":"object","properties":{"terms":{"type":"array","items":{"type":"object","properties":{"term":{"type":"string"},"keep":{"type":"boolean"}},"required":["term","keep"],"additionalProperties":false}}},"required":["terms"],"additionalProperties":false}
            """.strip();
}
