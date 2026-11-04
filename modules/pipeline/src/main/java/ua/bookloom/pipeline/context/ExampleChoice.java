package ua.bookloom.pipeline.context;

import java.util.Objects;

/**
 * What block 6 shows (design D9).
 *
 * @param kind where the text came from
 * @param text the block's text; empty exactly when {@code kind} is {@link Kind#NONE}
 */
public record ExampleChoice(Kind kind, String text) {

    /** Where the examples came from. */
    public enum Kind {
        /** The pair file's own worked examples. */
        PAIR,
        /** The target file's illustration under its label. */
        TARGET,
        /** No examples: neither file has any, or the model's profile turns them off. */
        NONE
    }

    /** The choice of no examples. */
    public static final ExampleChoice NONE = new ExampleChoice(Kind.NONE, "");

    /** Rejects a missing part and a text that disagrees with the kind. */
    public ExampleChoice {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        if ((kind == Kind.NONE) != text.isEmpty()) {
            throw new IllegalArgumentException("examples of kind " + kind + " with text length " + text.length());
        }
    }
}
