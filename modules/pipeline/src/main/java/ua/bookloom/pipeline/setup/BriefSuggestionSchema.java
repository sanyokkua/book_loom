package ua.bookloom.pipeline.setup;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The strict response schema of the Book Brief suggestion. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BriefSuggestionSchema {

    /**
     * An object holding the six fields the brief suggestion of one passage answers with. Each field is a short chain of
     * thought, the quote first and the label after it, then a confidence; the narrator's gender also carries the name
     * the quote calls the narrator by, which the code genders itself.
     */
    public static final String SCHEMA = schema("");

    /**
     * As {@link #SCHEMA} with the passage's language first, which only a book whose source language is Auto asks for:
     * nobody told the model which language the passage is in, so it names it.
     */
    public static final String WITH_LANGUAGE = schema("\"language\":{\"type\":\"string\"},");

    private static String schema(final String languageProperty) {
        return """
                {"type":"object","properties":{%s\
                "genre":%s,\
                "register":%s,\
                "voice":%s,\
                "audience":%s,\
                "narrator":%s,\
                "narratorGender":%s},\
                "required":[%s"genre","register","voice","audience","narrator","narratorGender"],\
                "additionalProperties":false}""".formatted(
                        languageProperty,
                        field("{\"type\":\"string\"}"),
                        field("{\"type\":\"string\",\"enum\":[\"formal\",\"neutral\",\"casual\"]}"),
                        field("{\"type\":\"string\"}"),
                        field("{\"type\":\"string\"}"),
                        field("{\"type\":\"string\",\"enum\":[\"first\",\"third\",\"unspecified\"]}"),
                        namedField("{\"type\":\"string\",\"enum\":[\"male\",\"female\",\"unknown\"]}"),
                        languageProperty.isEmpty() ? "" : "\"language\",");
    }

    private static String namedField(final String value) {
        return """
                {"type":"object","properties":{"evidence":{"type":"string"},"value":%s,"name":{"type":"string"},\
                "confidence":{"type":"number"}},"required":["evidence","value","name","confidence"],\
                "additionalProperties":false}""".formatted(value);
    }

    private static String field(final String value) {
        return """
                {"type":"object","properties":{"evidence":{"type":"string"},"value":%s,\
                "confidence":{"type":"number"}},"required":["evidence","value","confidence"],\
                "additionalProperties":false}""".formatted(value);
    }
}
