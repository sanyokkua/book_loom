package ua.bookloom.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The replies a scripted model answers with, in the shape each kind of call is read in. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestReplies {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A translate-family reply of one item, the shape a draft, its re-ask and its repair are read in. */
    public static String targetReply(final String target) {
        return "{\"items\":[{\"id\":\"1\",\"target\":" + quoted(target) + "}]}";
    }

    /** A single-target reply, the shape a review retry's draft, a directed fix and a revision are read in. */
    public static String singleTargetReply(final String target) {
        return "{\"target\":" + quoted(target) + "}";
    }

    private static String quoted(final String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode scripted target", cause);
        }
    }
}
