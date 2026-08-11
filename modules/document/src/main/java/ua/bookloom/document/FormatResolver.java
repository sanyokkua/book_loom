package ua.bookloom.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.document.model.CorruptContainerException;

/**
 * Resolves a book's format from its file-name extension and confirms that resolution against its leading bytes.
 *
 * <p><strong>Why the extension decides and the bytes only confirm.</strong> Every valid plain-text file is also a
 * valid Markdown file — nothing in the bytes separates them — so the file name is not a hint here, it is the only
 * signal there is. The leading bytes are still checked, because {@code .epub} and {@code .fb2.zip} are both zip
 * archives and would otherwise be interchangeable.
 *
 * <p><strong>Why not sniff, and why not try each reader.</strong> Trying each parser and keeping whichever
 * succeeds is worse than useless: Markdown accepts literally any bytes, so a corrupt EPUB would parse as a
 * Markdown book whose text is the base64 of a zip, and the user would discover it only after translating it. A
 * failure that produces a plausible wrong answer beats no answer only in the wrong direction.
 *
 * <p>The awkward inputs are real, not hypothetical: a downloaded book can arrive as a {@code .txt.zip} bundle
 * carrying images and shortcuts alongside the text, or as a <em>directory</em> whose name ends in {@code .fb2}.
 * Each is refused with its own reason before any parser runs.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FormatResolver {

    /** A zip archive's local-file-header signature, {@code PK}. */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    private static final int PROLOG_PROBE_BYTES = 1024;
    private static final String FICTION_BOOK_ROOT = "<FictionBook";

    /**
     * Resolves {@code source}'s format.
     *
     * @param source the file to open
     * @return the resolved format
     * @throws CorruptContainerException if the path is not a regular file, its extension names no supported
     *     format, or its leading bytes contradict the format its extension names
     */
    static BookFormat resolve(Path source) {
        Objects.requireNonNull(source, "source");
        refuseUnlessRegularFile(source);
        final String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        final BookFormat candidate = byExtension(name);
        confirmAgainstContent(source, name, candidate);
        return candidate;
    }

    /**
     * A directory named {@code book.fb2} is a real thing the corpus supplied. It has to fail as "not a file"
     * rather than as an invalid EPUB, which is what a reader would have said.
     */
    private static void refuseUnlessRegularFile(Path source) {
        if (!Files.isRegularFile(source)) {
            throw new CorruptContainerException("This is not a file that can be opened as a book");
        }
    }

    private static BookFormat byExtension(String name) {
        if (name.endsWith(".epub")) {
            return BookFormat.EPUB;
        }
        if (name.endsWith(".fb2") || name.endsWith(".fb2.zip")) {
            return BookFormat.FB2;
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return BookFormat.MARKDOWN;
        }
        if (name.endsWith(".txt")) {
            return BookFormat.TXT;
        }
        throw new CorruptContainerException("This file type is not one this application can open");
    }

    /**
     * Confirms the extension against the leading bytes: a zip signature where a container is expected, and a
     * FictionBook root element where a bare FB2 is. Markdown and TXT are deliberately unconfirmed — any bytes are
     * valid content for both, so there is nothing a content check could establish.
     */
    private static void confirmAgainstContent(Path source, String name, BookFormat candidate) {
        final byte[] prolog = readProlog(source);
        if (candidate == BookFormat.EPUB || name.endsWith(".fb2.zip")) {
            refuseUnless(startsWithZipMagic(prolog), "This file does not contain the archive its name promises");
            return;
        }
        if (candidate == BookFormat.FB2) {
            refuseUnless(looksLikeFictionBook(prolog), "This file does not contain the FictionBook its name promises");
        }
    }

    private static void refuseUnless(boolean confirmed, String reason) {
        if (!confirmed) {
            throw new CorruptContainerException(reason);
        }
    }

    private static boolean startsWithZipMagic(byte[] prolog) {
        return prolog.length >= ZIP_MAGIC.length
                && Arrays.equals(prolog, 0, ZIP_MAGIC.length, ZIP_MAGIC, 0, ZIP_MAGIC.length);
    }

    /**
     * Looks for the root element rather than requiring an XML declaration, because a real FB2 may open with a
     * byte-order mark, whitespace, a declaration, or the root element directly. Read as ISO-8859-1 so every byte
     * maps to a character and the search works whatever the document's real encoding turns out to be.
     */
    private static boolean looksLikeFictionBook(byte[] prolog) {
        return new String(prolog, java.nio.charset.StandardCharsets.ISO_8859_1).contains(FICTION_BOOK_ROOT);
    }

    private static byte[] readProlog(Path source) {
        try (InputStream in = Files.newInputStream(source)) {
            return in.readNBytes(PROLOG_PROBE_BYTES);
        } catch (IOException e) {
            throw new CorruptContainerException("Unable to read this file", e);
        }
    }
}
