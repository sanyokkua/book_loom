package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Writes an invented eval book as FB2, the format whose sections and section titles the importer turns into chapters
 * and headings, so a case can be a whole small book with front matter and chapters rather than a list of lines.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Fb2Fixture {

    /**
     * One section of the book.
     *
     * @param title the section title, or null for a section with none
     * @param paragraphs the section's paragraphs, in order
     */
    record Section(@Nullable String title, List<String> paragraphs) {

        Section {
            paragraphs = List.copyOf(paragraphs);
        }
    }

    /** The book's metadata: the file stem, the title, the author as first and last name, and the language. */
    record Meta(String fileStem, String title, String author, String language) {

        Meta {
            Objects.requireNonNull(fileStem, "fileStem");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(language, "language");
        }
    }

    /** Writes the book into {@code dir} as {@code <fileStem>.fb2} and returns its path. */
    static Path write(final Path dir, final Meta meta, final List<Section> sections) {
        Objects.requireNonNull(dir, "dir");
        final String[] author = meta.author().split(" ", 2);
        final String body = sections.stream().map(Fb2Fixture::section).collect(Collectors.joining("\n"));
        final String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
            <description><title-info>
            <author><first-name>%s</first-name><last-name>%s</last-name></author>
            <book-title>%s</book-title><lang>%s</lang>
            </title-info></description>
            <body>%s</body>
            </FictionBook>
            """.formatted(
                        escaped(author[0]),
                        escaped(author.length > 1 ? author[1] : ""),
                        escaped(meta.title()),
                        meta.language(),
                        body);
        try {
            return Files.writeString(dir.resolve(meta.fileStem() + ".fb2"), xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String section(final Section section) {
        final String title = section.title() == null ? "" : "<title><p>" + escaped(section.title()) + "</p></title>";
        final String paragraphs = section.paragraphs().stream()
                .map(text -> "<p>" + escaped(text) + "</p>")
                .collect(Collectors.joining("\n"));
        return "<section>" + title + "\n" + paragraphs + "</section>";
    }

    private static String escaped(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
