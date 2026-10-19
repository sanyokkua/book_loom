package ua.bookloom.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes a long synthetic book for the soak run: the same seeded paragraphs as Markdown or as plain text, with
 * chapter headings, recurring names, inline emphasis and links that become placeholders, number- and symbol-only
 * paragraphs, and a few paragraphs long enough to be drafted in pieces.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SoakBooks {

    /** Paragraphs between two chapter headings. */
    private static final int PER_CHAPTER = 50;
    /** Every this many paragraphs one is a huge one. */
    private static final int HUGE_EVERY = 900;

    private static final int HUGE_SENTENCES = 160;
    private static final int MAX_SENTENCES = 4;

    private static final List<String> NAMES =
            List.of("Eleanor Vance", "Tomas Reyes", "Harrow Vale", "the Meridian Institute", "Nell", "Captain Orrin");
    private static final List<String> VERBS =
            List.of("walked to", "looked at", "wrote about", "remembered", "spoke of", "measured", "crossed");
    private static final List<String> THINGS = List.of(
            "the old observatory",
            "the gravity well",
            "the northern ridge",
            "a brass instrument",
            "the quiet harbour",
            "the long corridor",
            "the river delta");
    private static final List<String> ODD_PARAGRAPHS = List.of("42", "XIV", "§ 7", "1914", "—", "9.81");

    /** The two shapes the book is written in. */
    enum Shape {
        MARKDOWN("md"),
        TXT("txt");

        private final String extension;

        Shape(final String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }
    }

    /**
     * Writes the book.
     *
     * @param dir the directory to write into
     * @param shape Markdown or plain text
     * @param paragraphs how many body paragraphs, headings not counted
     * @param seed the seed every word is chosen with
     * @return the written file
     */
    static Path write(final Path dir, final Shape shape, final int paragraphs, final long seed) {
        final Random random = new Random(seed);
        final List<String> blocks = new ArrayList<>();
        IntStream.range(0, paragraphs).forEach(index -> blocks.addAll(blocksAt(index, shape, random)));
        final Path file = dir.resolve("Soak." + shape.extension());
        try {
            return Files.writeString(file, String.join("\n\n", blocks) + "\n", StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static List<String> blocksAt(final int index, final Shape shape, final Random random) {
        final List<String> blocks = new ArrayList<>(2);
        if (index % PER_CHAPTER == 0) {
            final String title = "Chapter " + (index / PER_CHAPTER + 1) + ": " + pick(random, THINGS);
            blocks.add(shape == Shape.MARKDOWN ? "# " + title : title.toUpperCase(java.util.Locale.ROOT));
        }
        blocks.add(paragraph(index, shape, random));
        return blocks;
    }

    private static String paragraph(final int index, final Shape shape, final Random random) {
        if (index % HUGE_EVERY == HUGE_EVERY - 1) {
            return sentences(HUGE_SENTENCES, shape, random);
        }
        if (index % 97 == 13) {
            return pick(random, ODD_PARAGRAPHS);
        }
        return sentences(1 + random.nextInt(MAX_SENTENCES), shape, random);
    }

    private static String sentences(final int count, final Shape shape, final Random random) {
        return IntStream.range(0, count)
                .mapToObj(ignored -> sentence(shape, random))
                .collect(Collectors.joining(" "));
    }

    private static String sentence(final Shape shape, final Random random) {
        final String name = pick(random, NAMES);
        final String thing = pick(random, THINGS);
        final String marked = shape == Shape.MARKDOWN ? markup(thing, random) : thing;
        final String subject = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return subject + " " + pick(random, VERBS) + " " + marked + " in " + (1800 + random.nextInt(200)) + ".";
    }

    // Emphasis, strong text and links each become a placeholder pair or a standalone placeholder.
    private static String markup(final String text, final Random random) {
        return switch (random.nextInt(6)) {
            case 0 -> "*" + text + "*";
            case 1 -> "**" + text + "**";
            case 2 -> "[" + text + "](https://example.org/" + random.nextInt(50) + ")";
            default -> text;
        };
    }

    private static String pick(final Random random, final List<String> from) {
        return from.get(random.nextInt(from.size()));
    }
}
