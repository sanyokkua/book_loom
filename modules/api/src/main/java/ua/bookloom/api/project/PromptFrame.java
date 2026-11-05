package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.pipeline.PromptSection;

/**
 * The fixed part of a run's translate-family prompts (blocks 1–7 of design D8: role, the data sentence, the book rules,
 * the style sheet, the language rules, the examples and the reply line), stored once per project for each distinct
 * frame, since every call of a run sends it byte for byte. A segment's {@link ContextSnapshot} names it by hash.
 *
 * @param hash the SHA-256 of the frame's system text, lower-case hex; never blank
 * @param sections the frame's parts as sent, in order; copied
 */
public record PromptFrame(String hash, List<PromptSection> sections) {

    /** Rejects a missing or blank hash and copies the sections. */
    public PromptFrame {
        Objects.requireNonNull(hash, "hash");
        if (hash.isBlank()) {
            throw new IllegalArgumentException("A prompt frame needs a hash");
        }
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    }
}
