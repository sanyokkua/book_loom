package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * One filled part of a prompt as it was sent, in the order the prompt carries it: a block such as the glossary or the
 * previous pairs, or a standalone slot such as the style sheet or the items. A part the call left empty was not sent
 * and has no section.
 *
 * @param slot the template slot the part was filled from ({@code glossaryTerms}, {@code styleSheet}), stable across
 *     languages, which a screen may name the part by
 * @param heading the template's own heading line of the part ({@code [Glossary — …]}, {@code Style:}), or empty when
 *     the part has none
 * @param origin which message of the request carried it
 * @param lines the part's text as sent, one entry per line, the heading excluded
 */
public record PromptSection(String slot, String heading, Origin origin, List<String> lines) {

    /** Which message of a request a section was sent in. */
    public enum Origin {
        /** The system message: the rules, the style sheet, the language rules and the examples. */
        SYSTEM,
        /** The user message: the context blocks and the text to work on. */
        USER
    }

    /** Rejects a missing part and copies the lines. */
    public PromptSection {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(origin, "origin");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
}
