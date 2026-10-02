package ua.bookloom.pipeline.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The bundled few-shot examples a translating prompt shows: real source sentences and the literal reply each one
 * deserves, so a small model sees what a token kept around its words, a heading or a number-only paragraph looks like
 * in its target language instead of reading abstract rules alone. The file for the language pair wins, then the file
 * for the target language, then the language-independent {@code neutral} file; each is read from the classpath once
 * per pair. A line starting with {@code #} is a maintainer's note — such as the pairs an example declares — and never
 * reaches the model.
 */
@Slf4j
final class PromptExamples {

    static final String DIRECTORY = "examples/";
    static final String NEUTRAL = "neutral";
    private static final String SUFFIX = ".txt";
    private static final String COMMENT = "#";

    private final PromptTemplates.ResourceLoader loader;
    private final Map<String, String> byPair = new ConcurrentHashMap<>();

    PromptExamples(final PromptTemplates.ResourceLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * The examples for a language pair.
     *
     * @param sourceTag the source language tag, or null when it is inferred from the text, which skips the pair file
     * @param targetTag the non-null target language tag
     * @return the examples as the prompt shows them; never blank
     */
    String forPair(@Nullable final String sourceTag, final String targetTag) {
        Objects.requireNonNull(targetTag, "targetTag");
        final List<String> candidates = candidates(sourceTag, targetTag);
        return byPair.computeIfAbsent(String.join("|", candidates), key -> load(candidates));
    }

    /**
     * The file names tried for a pair, most specific first: {@code <source>-<target>}, {@code <target>} and
     * {@code neutral}, each language by its primary subtag.
     */
    static List<String> candidates(@Nullable final String sourceTag, final String targetTag) {
        final String target = primary(targetTag);
        final List<String> names = new ArrayList<>();
        if (sourceTag != null && !primary(sourceTag).isEmpty()) {
            names.add(primary(sourceTag) + "-" + target);
        }
        if (!target.isEmpty()) {
            names.add(target);
        }
        names.add(NEUTRAL);
        return List.copyOf(names);
    }

    private String load(final List<String> candidates) {
        for (final String name : candidates) {
            final String text = read(DIRECTORY + name + SUFFIX);
            if (text != null) {
                log.debug("Chose prompt examples file={} candidates={}", name, candidates);
                return withoutNotes(text);
            }
            log.debug("No prompt examples file={}", name);
        }
        throw new IllegalStateException("The bundled " + NEUTRAL + SUFFIX + " prompt examples are missing");
    }

    private @Nullable String read(final String fileName) {
        try (InputStream stream = loader.open(fileName)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new IllegalStateException("Prompt examples " + fileName + " could not be read", cause);
        }
    }

    /** Drops the maintainer's notes and the blank lines they leave behind. */
    static String withoutNotes(final String text) {
        final String kept =
                text.lines().filter(line -> !line.startsWith(COMMENT)).collect(Collectors.joining("\n"));
        return kept.replaceAll("\n{3,}", "\n\n").strip();
    }

    private static String primary(final String tag) {
        return Locale.forLanguageTag(tag.strip()).getLanguage();
    }
}
