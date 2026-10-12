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

    /**
     * The book's metadata: the file stem, the title, the author as first and last name, the language and, when given,
     * the year the book was written (FB2's {@code <date>}) and its publisher ({@code <publish-info>}). A blank title or
     * author leaves that element out, which is a book with no metadata.
     */
    record Meta(
            String fileStem,
            String title,
            String author,
            String language,
            @Nullable Integer year,
            @Nullable String publisher) {

        Meta {
            Objects.requireNonNull(fileStem, "fileStem");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(language, "language");
        }

        /** A book with a title, an author and a language only. */
        Meta(final String fileStem, final String title, final String author, final String language) {
            this(fileStem, title, author, language, null, null);
        }
    }

    /** Writes the book into {@code dir} as {@code <fileStem>.fb2} and returns its path. */
    static Path write(final Path dir, final Meta meta, final List<Section> sections) {
        Objects.requireNonNull(dir, "dir");
        final String body = sections.stream().map(Fb2Fixture::section).collect(Collectors.joining("\n"));
        final String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
            <description><title-info>%s%s%s<lang>%s</lang>
            </title-info>%s</description>
            <body>%s</body>
            </FictionBook>
            """.formatted(
                        authorElement(meta.author()),
                        titleElement(meta.title()),
                        meta.year() == null ? "" : "<date>" + meta.year() + "</date>",
                        meta.language(),
                        publishInfo(meta.publisher()),
                        body);
        try {
            return Files.writeString(dir.resolve(meta.fileStem() + ".fb2"), xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String authorElement(final String author) {
        if (author.isBlank()) {
            return "";
        }
        final String[] parts = author.split(" ", 2);
        return "<author><first-name>%s</first-name><last-name>%s</last-name></author>\n"
                .formatted(escaped(parts[0]), escaped(parts.length > 1 ? parts[1] : ""));
    }

    private static String titleElement(final String title) {
        return title.isBlank() ? "" : "<book-title>" + escaped(title) + "</book-title>";
    }

    private static String publishInfo(@Nullable final String publisher) {
        return publisher == null || publisher.isBlank()
                ? ""
                : "<publish-info><publisher>" + escaped(publisher) + "</publisher></publish-info>";
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
