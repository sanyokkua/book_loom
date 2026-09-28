package ua.bookloom.document.epub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Document;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;

/**
 * Writes a JDOM document back the way its source was written: line-feed line ends only, and an XML declaration only
 * when the source bytes began with one, naming the encoding that declaration named. Raw JDOM output adds a
 * declaration and carriage returns the source never had, which the canonical comparison cannot see because it
 * re-serializes both sides alike (the package document, and the NCX and navigation document of task 6.3).
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class XmlDocumentSerializer {

    private static final Pattern DECLARATION = Pattern.compile("^(?:\uFEFF)?<\\?xml\\s[^>]*\\?>");
    private static final Pattern ENCODING = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    /**
     * Serializes {@code document} under the source's own declaration and line-end rules.
     *
     * @param document the tree to write
     * @param sourceBytes the bytes the tree was parsed from; only their leading declaration is read
     * @return the serialized bytes, encoded in the source's declared encoding (UTF-8 when none was named)
     */
    static byte[] serialize(Document document, byte[] sourceBytes) {
        final String prefix =
                new String(sourceBytes, 0, Math.min(sourceBytes.length, 200), StandardCharsets.ISO_8859_1);
        final Matcher declaration = DECLARATION.matcher(prefix);
        final boolean hadDeclaration = declaration.find();
        final Charset charset = hadDeclaration ? declaredCharset(declaration.group()) : StandardCharsets.UTF_8;
        log.debug("XML source declaration present={} encoding={}", hadDeclaration, charset.name());
        final Format format = Format.getRawFormat()
                .setLineSeparator(LineSeparator.UNIX)
                .setOmitDeclaration(!hadDeclaration)
                .setEncoding(charset.name());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new XMLOutputter(format).output(document, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to serialize XML document", e);
        }
        return out.toByteArray();
    }

    private static Charset declaredCharset(String declaration) {
        final Matcher encoding = ENCODING.matcher(declaration);
        if (!encoding.find()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.group(1));
        } catch (IllegalArgumentException e) {
            log.debug("XML declaration names an unsupported encoding; writing UTF-8");
            return StandardCharsets.UTF_8;
        }
    }
}
