package ua.bookloom.pipeline.setup;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The strict response schema of the Book Brief suggestion. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BriefSuggestionSchema {

    /** An object holding the six fields the brief suggestion answers with. */
    public static final String SCHEMA = """
            {"type":"object","properties":{"genre":{"type":"string"},"register":{"type":"string","enum":["formal","neutral","casual"]},"voice":{"type":"string"},"audience":{"type":"string"},"narrator":{"type":"string","enum":["first","third","unspecified"]},"narratorGender":{"type":"string","enum":["male","female","unknown"]}},"required":["genre","register","voice","audience","narrator","narratorGender"],"additionalProperties":false}
            """.strip();
}
