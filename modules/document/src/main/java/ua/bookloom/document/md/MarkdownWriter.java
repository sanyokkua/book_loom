package ua.bookloom.document.md;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.model.BufferTextWriter;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * Writes a Markdown book back by copying the original bytes and substituting only the spans of segments that
 * carry target text.
 *
 * <p><strong>Nothing is re-rendered from the syntax tree.</strong> Re-rendering normalises formatting everywhere —
 * emphasis markers, bullet characters, fence styles, and whether the file ends with a newline — in parts of the
 * document nobody translated. Copying the original and replacing only translated spans makes every untouched byte
 * identical by construction, which is both stronger and simpler.
 *
 * <p><strong>A translation the source encoding cannot hold switches the whole file to UTF-8</strong> rather than
 * being written as {@code ?} (ADR-0029); see {@link BufferTextWriter}.
 *
 * <p><strong>Language metadata is replaced, never added.</strong> Markdown has a place for it only when the
 * frontmatter already declares a top-level {@code lang}; inventing one the source never had would add content that
 * was not there and break the byte-exact round trip for a field nothing reads.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class MarkdownWriter {

    private final OpenMarkdownRegistry registry;

    /**
     * Reassembles {@code document} and writes it to {@code destination}.
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage the tag an existing top-level frontmatter {@code lang} value is replaced with
     * @return {@code destination}
     * @throws DocumentNotOpenException if {@code document}'s id was never registered by
     *     {@link MarkdownReader#read}
     */
    public Path write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final ParsedMarkdown parsed =
                registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));

        final byte[] output = BufferTextWriter.assemble(
                parsed.originalBytes(),
                parsed.charset(),
                Boolean.TRUE.equals(document.hasBom()),
                MarkdownReplacements.of(document, parsed, targetLanguage),
                document.id(),
                document.format());
        try {
            Files.write(destination, output);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write Markdown output", e);
        }
        return destination;
    }
}
