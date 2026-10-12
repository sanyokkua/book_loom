package ua.bookloom.pipeline.setup;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Genre;

/** The strict response schema of the brief's synthesis (15i.B4). */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BriefSynthesisSchema {

    /** The five audiences the synthesis chooses from, as the brief stores them. */
    static final List<String> AUDIENCES = List.of("adult", "young adult", "children", "general", "academic");

    /**
     * Genre, audience and voice, each with its basis before its value, so the model names what a value stands on
     * before it decides it. The genre is one of the forty {@link Genre} names and the audience one of
     * {@link #AUDIENCES}, or empty, so a structured-output server can return nothing the brief cannot hold.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "genre":%s,\
            "audience":%s,\
            "voice":%s},\
            "required":["genre","audience","voice"],\
            "additionalProperties":false}""".formatted(
                    field(choice(Arrays.stream(Genre.values()).map(Genre::english))),
                    field(choice(AUDIENCES.stream())),
                    field("{\"type\":\"string\"}"));

    private static String choice(final Stream<String> names) {
        final String listed = Stream.concat(Stream.of(""), names)
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(","));
        return "{\"type\":\"string\",\"enum\":[" + listed + "]}";
    }

    private static String field(final String value) {
        return """
                {"type":"object","properties":{"basis":{"type":"object","properties":{\
                "knowledge":{"type":"boolean"},"passages":{"type":"array","items":{"type":"integer"}}},\
                "required":["knowledge","passages"],"additionalProperties":false},"value":%s},\
                "required":["basis","value"],"additionalProperties":false}""".formatted(value);
    }
}
