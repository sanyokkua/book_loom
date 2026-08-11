package ua.bookloom.document.md;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.BufferReassembler;
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
 * <p><strong>No language metadata is added.</strong> The export contract takes a target language for every format,
 * but Markdown has nowhere to put one. Inventing a place — a {@code lang:} key in a frontmatter block the source
 * never had — would add content that was not there and break the byte-exact round trip for a field nothing reads.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class MarkdownWriter {

    private final OpenMarkdownRegistry registry;

    /**
     * Reassembles {@code document} and writes it to {@code destination}.
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage accepted and deliberately unused — Markdown carries no language field
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

        final byte[] output = BufferReassembler.splice(parsed.originalBytes(), replacementsOf(document, parsed));
        try {
            Files.write(destination, output);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write Markdown output", e);
        }
        return destination;
    }

    private static List<BufferReassembler.Replacement> replacementsOf(Document document, ParsedMarkdown parsed) {
        final List<BufferReassembler.Replacement> replacements = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                addReplacement(replacements, segment, parsed);
            }
        }
        return replacements;
    }

    private static void addReplacement(
            List<BufferReassembler.Replacement> replacements, Segment segment, ParsedMarkdown parsed) {
        final String targetInner = segment.targetInner();
        if (targetInner == null) {
            return;
        }
        replacements.add(
                new BufferReassembler.Replacement(spanOf(segment.anchor()), targetInner.getBytes(parsed.charset())));
    }

    /**
     * A buffer skeleton can only be addressed by a byte span; a node path indexes a tree this format does not
     * have, so reaching here with one is a programming error rather than a data error.
     */
    private static ByteSpanAnchor spanOf(SkeletonAnchor anchor) {
        return switch (anchor) {
            case ByteSpanAnchor span -> span;
            case ua.bookloom.api.document.NodeAnchor ignored ->
                throw new IllegalArgumentException("A buffer skeleton cannot be addressed by a node path");
        };
    }
}
