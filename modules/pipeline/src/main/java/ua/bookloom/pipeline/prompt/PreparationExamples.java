package ua.bookloom.pipeline.prompt;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The worked examples of the names-pass prompts in the book's language, read from the bundled language files: the
 * source language's file when it sets them ({@code prepExample.<kind>.N} and {@code prepExample.<kind>.reply}), else
 * the generic file's neutral ones. Never the target's or the pair's file: these prompts read the book, so their
 * examples show the book's language; a small model copies the language of its examples.
 */
@Slf4j
final class PreparationExamples {

    private static final String PREFIX = "prepExample.";
    private static final String REPLY = ".reply";

    private final LanguageRules rules;
    private final Map<String, PreparationExample> chosen = new ConcurrentHashMap<>();

    PreparationExamples(final LanguageRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * The examples of one prompt.
     *
     * @param sourceTag the chosen source language, or null under Auto, which reads the generic examples
     * @param kind the prompt's key: {@code termReading}, {@code lexiconReading}, {@code prescan} or {@code reviewTerms}
     * @return the example lines and the valid reply, read once per language and kind
     */
    PreparationExample of(@Nullable final String sourceTag, final String kind) {
        Objects.requireNonNull(kind, "kind");
        final String prefix = PREFIX + kind;
        final String language = sourceTag == null ? "" : sourceTag;
        return chosen.computeIfAbsent(prefix + "|" + language, ignored -> {
            final LanguageFile own = rules.languageFile(sourceTag);
            final LanguageFile file = rules.genericOnly || own.indexed(prefix).isEmpty() ? rules.generic : own;
            final String reply = file.get(prefix + REPLY);
            if (file.indexed(prefix).isEmpty() || reply == null) {
                throw new IllegalStateException(file.name() + " lacks the examples " + prefix);
            }
            log.debug("Chose the {} examples from {} for source {}", kind, file.name(), sourceTag);
            return new PreparationExample(String.join("\n", file.indexed(prefix)), reply);
        });
    }
}
