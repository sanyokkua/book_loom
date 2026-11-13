package ua.bookloom.llm.provider;

import java.util.Objects;
import ua.bookloom.api.llm.ChatResponse;

/**
 * The answer to one Advanced features check, with what the reply showed of the model's reasoning: the thinking check
 * is honoured only when a reply carries no reasoning, which the cleaned answer alone no longer shows.
 *
 * @param response the answer, cleaned as every reply is; non-null
 * @param reasoning what the reply showed of reasoning; non-null
 */
public record CheckReply(ChatResponse response, Reasoning reasoning) {

    /** Checks both components. */
    public CheckReply {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(reasoning, "reasoning");
    }

    /** What a reply showed of the model's reasoning. */
    public enum Reasoning {
        /** A reasoning field held text, the content held a think block, or reasoning tokens were counted. */
        SHOWN,
        /** The reply was read and carried no reasoning. */
        NONE,
        /** The client cannot read reasoning from its replies. */
        UNKNOWN
    }
}
