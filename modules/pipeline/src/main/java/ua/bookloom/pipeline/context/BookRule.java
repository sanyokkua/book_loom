package ua.bookloom.pipeline.context;

import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.RuleKey;

/**
 * A writing rule the run states once in its fixed part only when the book needs it (design D9), in the order the
 * lines are sent. Each names its key in the language files; one that is also a language-rules key is left out of that
 * section so the model is not told it twice.
 */
public enum BookRule {
    /** How to carry the {@code ⟦gN⟧} marks; sent when a story segment holds one. */
    MARKS("marks", null),
    /** The target's dialogue form; sent when the story has enough dialogue. */
    DIALOGUE("dialogue", RuleKey.DIALOGUE),
    /** One form of address per conversation; sent with the dialogue rule. */
    ADDRESS("address", RuleKey.ADDRESS),
    /** How numbers are kept and written; sent when the story holds a digit. */
    NUMBERS("numbers", RuleKey.NUMBERS),
    /** Gender agreement; sent when the cast has a gender and the target shows gendered past forms. */
    AGREEMENT("agreement", RuleKey.AGREEMENT),
    /** Glossary names decline; sent when the target declines names. */
    INFLECT("inflect", null);

    private final String key;
    private final @Nullable RuleKey sectionKey;

    BookRule(final String key, @Nullable final RuleKey sectionKey) {
        this.key = key;
        this.sectionKey = sectionKey;
    }

    /** The key the language files state the rule under, as {@code rule.<key>} or the plain key. */
    public String key() {
        return key;
    }

    /** The language-rules key the same rule has there, or null when the section states no such rule. */
    public @Nullable RuleKey sectionKey() {
        return sectionKey;
    }
}
