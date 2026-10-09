package ua.bookloom.pipeline.setup;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The strict response schema of the Book Brief suggestion. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BriefSuggestionSchema {

    /**
     * An object holding the six fields the brief suggestion answers with, each a value, its quote and a confidence; the
     * narrator's gender also carries the name the quote calls the narrator by, which the code genders itself.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "genre":%s,\
            "register":%s,\
            "voice":%s,\
            "audience":%s,\
            "narrator":%s,\
            "narratorGender":%s},\
            "required":["genre","register","voice","audience","narrator","narratorGender"],\
            "additionalProperties":false}""".formatted(
                    field("{\"type\":\"string\"}"),
                    field("{\"type\":\"string\",\"enum\":[\"formal\",\"neutral\",\"casual\"]}"),
                    field("{\"type\":\"string\"}"),
                    field("{\"type\":\"string\"}"),
                    field("{\"type\":\"string\",\"enum\":[\"first\",\"third\",\"unspecified\"]}"),
                    namedField("{\"type\":\"string\",\"enum\":[\"male\",\"female\",\"unknown\"]}"));

    private static String namedField(final String value) {
        return """
                {"type":"object","properties":{"value":%s,"evidence":{"type":"string"},"name":{"type":"string"},\
                "confidence":{"type":"number"}},"required":["value","evidence","name","confidence"],\
                "additionalProperties":false}""".formatted(value);
    }

    private static String field(final String value) {
        return """
                {"type":"object","properties":{"value":%s,"evidence":{"type":"string"},\
                "confidence":{"type":"number"}},"required":["value","evidence","confidence"],\
                "additionalProperties":false}""".formatted(value);
    }
}
