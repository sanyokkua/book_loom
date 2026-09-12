package ua.bookloom.document.fixture;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * FB2 fixtures, written as real files into a {@code @TempDir}.
 *
 * <p>Built in Java rather than committed, for the reason design.md D8 gives: the primary fixture's whole point is
 * that it is a <strong>{@code windows-1251} byte sequence</strong>, and a committed file of that kind is one
 * editor save, one {@code core.autocrlf} setting or one formatter run away from being silently normalised into a
 * fixture that no longer tests what it was written to test — with a <em>passing</em> test as the failure mode.
 * {@code xml.getBytes(Charset.forName("windows-1251"))} cannot be normalised by anything.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Fb2Fixtures {

    /** The declared encoding of {@link #PRIMARY_XML}, spelled as a real book spells it. */
    public static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    /**
     * The primary FB2 fixture, carrying every shape the survey proved has <strong>no natural example</strong> in
     * the corpus — a non-UTF-8 declaration, a notes body, a table, a CDATA section and an XML comment — alongside
     * the ones it does: a {@code <binary>} cover, a poem with a stanza and verse lines, {@code <lang>} beside
     * {@code <src-lang>}, an {@code <empty-line/>}, a named entity, an image-only paragraph and a line-break run.
     */
    public static final String PRIMARY_XML = """
            <?xml version="1.0" encoding="windows-1251"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:l="http://www.w3.org/1999/xlink">
              <description>
                <title-info>
                  <book-title>Енеїда</book-title>
                  <author><first-name>Іван</first-name><last-name>Котляревський</last-name></author>
                  <lang>uk</lang>
                  <src-lang>en</src-lang>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>Розділ перший</p></title>
                  <subtitle>Підзаголовок</subtitle>
                  <p>Еней був парубок моторний.</p>
                  <empty-line/>
                  <p><image l:href="#cover.jpg"/></p>
                  <p>Пролог<br/>Хвіст комети</p>
                  <poem><stanza><v>Рядок перший</v><v>Рядок другий</v><v>Рядок третій</v></stanza></poem>
                  <cite><p>Цитоване речення.</p></cite>
                  <table><tr><td>Комірка А</td><td>Комірка Б</td><td>Комірка В</td></tr>
                         <tr><td>Комірка Г</td><td>Комірка Д</td><td>Комірка Е</td></tr></table>
                  <p><![CDATA[a < b]]></p>
                  <!-- scene break -->
                  <p>Останній <emphasis>абзац</emphasis> розділу.</p>
                  <p>Посилання на примітку<a l:href="#n1" type="note">1</a>.</p>
                </section>
              </body>
              <body name="notes">
                <section id="n1"><p>Текст примітки.</p></section>
              </body>
              <binary id="cover.jpg" content-type="image/jpeg">iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==</binary>
            </FictionBook>
            """;

    /**
     * A character {@code windows-1251} cannot represent. The encoding switch is a <em>write</em>-side fixture by
     * necessity: a source file declaring {@code windows-1251} cannot itself contain this character, so the case
     * only exists once a translation introduces one (EC-FB2-1).
     */
    public static final String UNREPRESENTABLE_IN_WINDOWS_1251 = "車";

    /** An FB2 declaring a Western code page over Cyrillic bytes — EC-FB2-2's contradiction. */
    public static final String CONTRADICTED_DECLARATION_XML =
            PRIMARY_XML.replace("encoding=\"windows-1251\"", "encoding=\"windows-1252\"");

    /**
     * A minimal FB2 whose {@code <title-info>} omits {@code <lang>} entirely — the shape one surveyed book
     * proved is not hypothetical, and which {@link #PRIMARY_XML} never exercises because it declares both
     * {@code <lang>} and {@code <src-lang>}. This is the writer's add-branch, round-tripped for the first time.
     */
    public static final String NO_LANGUAGE_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info>
                  <genre>prose</genre><book-title>Sample</book-title>
              </title-info></description>
              <body><section><p>Hello world.</p></section></body>
            </FictionBook>
            """;

    /**
     * {@link #NO_LANGUAGE_XML} with an empty {@code <lang></lang>} declared instead of none at all. The reader
     * already records this as absent — an empty declaration is absence, not a value — but the writer must
     * <strong>replace</strong> this element's text rather than add a second one beside it.
     */
    public static final String EMPTY_LANGUAGE_XML =
            NO_LANGUAGE_XML.replace("<genre>prose</genre>", "<genre>prose</genre><lang></lang>");

    /**
     * A single paragraph carrying every hazard a mask-then-restore identity cycle must survive together: a CDATA
     * section, a namespaced note anchor, and a nested {@code <pre>} listing inside a text-owning {@code <div>} —
     * the requirement <em>Restore a masked segment to its source content when nothing is translated</em>.
     */
    public static final String HAZARD_PARAGRAPH_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:l="http://www.w3.org/1999/xlink">
              <description><title-info>
                  <genre>prose</genre><book-title>Sample</book-title><lang>uk</lang>
              </title-info></description>
              <body><section><p>Порівняй <![CDATA[a < b]]> тут, дивись <a l:href="#n1" type="note">1</a> і \
            <div>ось лістинг: <pre>code();
            line two</pre> кінець.</div> все.</p></section></body>
            </FictionBook>
            """;

    /**
     * Writes {@link #HAZARD_PARAGRAPH_XML} as a bare UTF-8 file.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path hazardParagraph(Path destination) {
        return writeFb2(destination, HAZARD_PARAGRAPH_XML, StandardCharsets.UTF_8);
    }

    /**
     * Writes {@link #PRIMARY_XML} as a bare {@code windows-1251} file.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path primary(Path destination) {
        return writeFb2(destination, PRIMARY_XML, WINDOWS_1251);
    }

    /**
     * Writes {@link #PRIMARY_XML} wrapped in a zip container, as a {@code .fb2.zip} arrives.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path primaryZipped(Path destination) {
        return writeFb2Zip(destination, "book.fb2", PRIMARY_XML, WINDOWS_1251);
    }

    /**
     * Writes {@link #NO_LANGUAGE_XML} as a bare UTF-8 file.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path noLanguage(Path destination) {
        return writeFb2(destination, NO_LANGUAGE_XML, StandardCharsets.UTF_8);
    }

    /**
     * Writes {@link #EMPTY_LANGUAGE_XML} as a bare UTF-8 file.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path emptyLanguage(Path destination) {
        return writeFb2(destination, EMPTY_LANGUAGE_XML, StandardCharsets.UTF_8);
    }

    /**
     * Writes {@code xml} encoded in {@code charset}.
     *
     * @param destination the file to write
     * @param xml the document text
     * @param charset the encoding to write it in
     * @return {@code destination}
     */
    public static Path writeFb2(Path destination, String xml, Charset charset) {
        try {
            Files.write(destination, xml.getBytes(charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }

    /**
     * Wraps {@code xml} in a zip container under {@code memberName}.
     *
     * @param destination the {@code .fb2.zip} file to write
     * @param memberName the member name inside the archive
     * @param xml the document text
     * @param charset the encoding to write the member in
     * @return {@code destination}
     */
    public static Path writeFb2Zip(Path destination, String memberName, String xml, Charset charset) {
        try (OutputStream out = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(memberName));
            zip.write(xml.getBytes(charset));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }

    /**
     * Writes a zip whose single entry is flagged encrypted, so the DRM refusal path has something to refuse. The
     * corpus has no genuinely protected book, so this shape exists only because it was hand-authored.
     *
     * @param destination the {@code .fb2.zip} file to write
     * @return {@code destination}
     */
    public static Path writeEncryptedFb2Zip(Path destination) {
        final byte[] plain = readAll(writeFb2Zip(destination, "book.fb2", PRIMARY_XML, WINDOWS_1251));
        return write(destination, withEncryptedFlagSet(plain));
    }

    /**
     * Sets bit 0 of the general-purpose flag in both the local file header and the central-directory entry — the
     * flag an archiver sets when it password-protects an entry. Flipping the bit is enough because the flag is
     * exactly what the importer adjudicates on: an entry that claims to be encrypted is refused before anything
     * tries to inflate it.
     */
    private static byte[] withEncryptedFlagSet(byte[] archive) {
        final byte[] flagged = archive.clone();
        setFlagBitAfter(flagged, 0x04034b50, 6);
        setFlagBitAfter(flagged, 0x02014b50, 8);
        return flagged;
    }

    private static void setFlagBitAfter(byte[] archive, int signature, int flagOffset) {
        final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(archive).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int at = 0; at + flagOffset + 2 <= archive.length; at++) {
            if (buffer.getInt(at) == signature) {
                buffer.putShort(at + flagOffset, (short) (buffer.getShort(at + flagOffset) | 0x0001));
                return;
            }
        }
        throw new IllegalStateException("No zip record with signature " + Integer.toHexString(signature));
    }

    private static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path write(Path destination, byte[] bytes) {
        try {
            Files.write(destination, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}
