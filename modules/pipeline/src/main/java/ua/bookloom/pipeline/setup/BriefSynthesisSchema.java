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

    // The narrator's gender with the quote that shows it and the name in that quote, which the code checks.
    private static final String NARRATOR = """
            {"type":"object","properties":{"gender":{"type":"string","enum":["male","female"]},\
            "quote":{"type":"string"},"name":{"type":"string"}},\
            "required":["gender","quote","name"],"additionalProperties":false}""";

    /** The two story shapes the synthesis chooses from, in its words: one story first, then a collection. */
    static final List<String> STORY_SHAPES = List.of("one story", "separate stories");

    // The story shape with its quotes before its value: each quote names the passage it was copied from, which the
    // code checks, so the shape stands on two windows compared with each other (design D6).
    private static final String STORY_SHAPE = """
            {"type":"object","properties":{"quotes":{"type":"array","items":{"type":"object","properties":{\
            "passage":{"type":"integer"},"quote":{"type":"string"}},\
            "required":["passage","quote"],"additionalProperties":false}},"value":%s},\
            "required":["quotes","value"],"additionalProperties":false}""".formatted(choice(STORY_SHAPES.stream()));

    /** The three registers the synthesis chooses from, in the brief suggestion's words. */
    static final List<String> REGISTERS = List.of("formal", "neutral", "casual");

    /**
     * Genre, audience, voice and register, each with its basis before its value, so the model names what a value
     * stands on before it decides it, and the narrator's most probable gender with the quote and the name that show
     * it. The genre is one of the forty {@link Genre} names, the audience one of {@link #AUDIENCES} and the register one
     * of {@link #REGISTERS}, with no empty value, so the model always gives its most probable one (owner decision
     * D-11) and a structured-output server can return nothing the brief cannot hold. The story shape is one of
     * {@link #STORY_SHAPES}, its quotes written first.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "genre":%s,\
            "audience":%s,\
            "voice":%s,\
            "register":%s,\
            "narrator":%s,\
            "storyShape":%s},\
            "required":["genre","audience","voice","register","narrator","storyShape"],\
            "additionalProperties":false}""".formatted(
                    field(choice(Arrays.stream(Genre.values()).map(Genre::english))),
                    field(choice(AUDIENCES.stream())),
                    field("{\"type\":\"string\"}"),
                    field(choice(REGISTERS.stream())),
                    NARRATOR,
                    STORY_SHAPE);

    private static String choice(final Stream<String> names) {
        final String listed = names.map(name -> "\"" + name + "\"").collect(Collectors.joining(","));
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
