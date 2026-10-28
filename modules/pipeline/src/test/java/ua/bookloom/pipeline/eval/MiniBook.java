package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;

/**
 * An invented mini eval book (15j.J15) as its committed source, {@code eval/datasets/<id>/book.json}, and the EPUB 3
 * written from it for each run. The source stays text so a reviewer can read the book and its expectations side by side; the
 * EPUB is generated, so no binary is committed.
 *
 * @param title the book's title
 * @param author the author's name
 * @param language the source language tag
 * @param pair the language pair, {@code source-target}, that a run translates
 * @param narrator who tells the book, so a run's brief states it as the dataset does
 * @param blocks the book's blocks in reading order
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MiniBook(String title, String author, String language, String pair, NarratorSpec narrator, List<Block> blocks) {

    static final String MINI_EN = "mini-en";
    static final String MINI_RU = "mini-ru";

    private static final String CONTAINER_NS = "urn:oasis:names:tc:opendocument:xmlns:container";
    private static final String XHTML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">";

    /** What a block is: a chapter heading, a paragraph or a footnote. */
    enum Kind {
        /** A chapter heading, which starts a content document. */
        H,
        /** A paragraph. */
        P,
        /** A footnote aside. */
        NOTE
    }

    /**
     * Who narrates, as the brief states it.
     *
     * @param person the narrator's grammatical person
     * @param gender the narrator's gender
     */
    record NarratorSpec(NarratorPerson person, Gender gender) {}

    /**
     * One block of the book.
     *
     * @param id the id the expectations name: {@code m01} onwards, or {@code <chapter file>:<line>} as the pipeline
     *     names a segment
     * @param kind what the block is
     * @param text its text, with no markup and none of the characters {@code & < >}
     * @param reference the target text the case author holds to be right, or null when the case has none
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Block(
            String id, Kind kind, String text, @Nullable String reference) {

        /** A block with no reference text. */
        Block(final String id, final Kind kind, final String text) {
            this(id, kind, text, null);
        }
    }

    /** Copies the blocks. */
    MiniBook {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(narrator, "narrator");
        blocks = List.copyOf(blocks);
    }

    /** The book of the dataset folder {@code eval/datasets/<dataset>}. */
    static MiniBook load(final String dataset) {
        final String resource = "/eval/datasets/" + dataset + "/book.json";
        try (InputStream in = MiniBook.class.getResourceAsStream(resource)) {
            return JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                    .build()
                    .readValue(Objects.requireNonNull(in, resource), MiniBook.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The source language of the pair. */
    String sourceLanguage() {
        return pair.substring(0, pair.indexOf('-'));
    }

    /** The target language of the pair. */
    String targetLanguage() {
        return pair.substring(pair.indexOf('-') + 1);
    }

    /**
     * Writes the book as an EPUB 3 with one content document per chapter and a navigation document.
     *
     * @param dir an existing directory
     * @return the file, {@code mini-book.epub} in {@code dir}
     */
    Path writeEpub(final Path dir) {
        final Path file = dir.resolve("mini-book.epub");
        final List<String> chapters = chapters();
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "mimetype", "application/epub+zip", ZipEntry.STORED);
            put(zip, "META-INF/container.xml", container(), ZipEntry.DEFLATED);
            put(zip, "OEBPS/content.opf", opf(chapters.size()), ZipEntry.DEFLATED);
            put(zip, "OEBPS/nav.xhtml", nav(), ZipEntry.DEFLATED);
            for (int i = 0; i < chapters.size(); i++) {
                put(zip, "OEBPS/ch" + i + ".xhtml", chapters.get(i), ZipEntry.DEFLATED);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private List<String> chapters() {
        final List<String> chapters = new ArrayList<>();
        StringBuilder body = null;
        for (final Block block : blocks) {
            if (block.kind() == Kind.H && body != null) {
                chapters.add(page(body));
                body = null;
            }
            body = body == null ? new StringBuilder() : body;
            body.append(markup(block));
        }
        if (body != null) {
            chapters.add(page(body));
        }
        return chapters;
    }

    private static String markup(final Block block) {
        return switch (block.kind()) {
            case H -> "<h1>" + block.text() + "</h1>";
            case P -> "<p>" + block.text() + "</p>";
            case NOTE -> "<aside epub:type=\"footnote\" id=\"" + block.id() + "\"><p>" + block.text() + "</p></aside>";
        };
    }

    private String page(final CharSequence body) {
        return XHTML_HEAD + "<head><title>" + title + "</title></head><body>" + body + "</body></html>";
    }

    private static String container() {
        return "<?xml version=\"1.0\"?><container xmlns=\"" + CONTAINER_NS + "\" version=\"1.0\"><rootfiles>"
                + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "</rootfiles></container>";
    }

    private String opf(final int chapterCount) {
        final StringBuilder manifest = new StringBuilder(
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>");
        final StringBuilder spine = new StringBuilder();
        for (int i = 0; i < chapterCount; i++) {
            manifest.append(
                    "<item id=\"c%d\" href=\"ch%d.xhtml\" media-type=\"application/xhtml+xml\"/>".formatted(i, i));
            spine.append("<itemref idref=\"c").append(i).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:identifier id=\"id\">bookloom-mini-eval</dc:identifier><dc:title>" + title + "</dc:title>"
                + "<dc:creator>" + author + "</dc:creator><dc:language>" + language + "</dc:language></metadata>"
                + "<manifest>" + manifest + "</manifest><spine>" + spine + "</spine></package>";
    }

    private String nav() {
        final StringBuilder items = new StringBuilder();
        int chapter = 0;
        for (final Block block : blocks) {
            if (block.kind() == Kind.H) {
                items.append("<li><a href=\"ch%d.xhtml\">%s</a></li>".formatted(chapter++, block.text()));
            }
        }
        return XHTML_HEAD + "<head><title>Contents</title></head><body><nav epub:type=\"toc\"><ol>" + items
                + "</ol></nav></body></html>";
    }

    private static void put(final ZipOutputStream zip, final String name, final String content, final int method)
            throws IOException {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method);
        if (method == ZipEntry.STORED) {
            final CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
