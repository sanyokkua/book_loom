package ua.bookloom.document.txt;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.BufferReassembler;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * Writes a plain-text book back by copying the original buffer and substituting only translated spans.
 *
 * <p><strong>The target language is accepted and ignored, deliberately.</strong> Plain text has nowhere to record
 * one. Inventing a place — a header line — would add content the source never had and break the byte-exact round
 * trip for a field nothing reads.
 *
 * <p><strong>An unrepresentable character fails the export loudly.</strong> FB2 solves the same problem by
 * switching to UTF-8 and rewriting its declaration; plain text has no declaration, so a reader has no way to learn
 * that the encoding changed. Silently re-encoding would make every non-Latin character in the file unreadable to
 * whatever opens it next, so the export fails instead and the user can re-import the book as UTF-8. The check runs
 * over every segment before a single byte is written, so a refused export leaves no partial file behind.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TxtWriter {

    private final OpenTxtRegistry registry;

    /**
     * Reassembles {@code document} and writes it to {@code destination}.
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage accepted and deliberately unused — plain text carries no language field
     * @return {@code destination}
     * @throws DocumentNotOpenException if {@code document}'s id was never registered by {@link TxtReader#read}
     * @throws CorruptContainerException if a segment's target text cannot be represented in the encoding the
     *     source was read with
     */
    public Path write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final OpenTxtRegistry.ParsedTxt parsed =
                registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));

        final List<BufferReassembler.Replacement> replacements = replacementsOf(document, parsed.charset());
        final byte[] output = BufferReassembler.splice(parsed.originalBytes(), replacements);
        try {
            Files.write(destination, output);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write plain-text output", e);
        }
        return destination;
    }

    /**
     * Encodes every translated segment up front. Doing the whole set before writing is what makes the refusal
     * leave no output file: the first unrepresentable character stops the export while the destination is still
     * untouched.
     */
    private static List<BufferReassembler.Replacement> replacementsOf(Document document, Charset charset) {
        final CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        final List<BufferReassembler.Replacement> replacements = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                addReplacement(replacements, segment, encoder, charset);
            }
        }
        return replacements;
    }

    private static void addReplacement(
            List<BufferReassembler.Replacement> replacements,
            Segment segment,
            CharsetEncoder encoder,
            Charset charset) {
        final String targetInner = segment.targetInner();
        if (targetInner == null) {
            return;
        }
        replacements.add(new BufferReassembler.Replacement(
                spanOf(segment.anchor()), encode(targetInner, encoder, charset, segment.id())));
    }

    private static byte[] encode(String targetInner, CharsetEncoder encoder, Charset charset, String segmentId) {
        try {
            encoder.reset();
            final java.nio.ByteBuffer encoded = encoder.encode(CharBuffer.wrap(targetInner));
            final byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException unrepresentable) {
            throw new CorruptContainerException(
                    "Segment " + segmentId + " contains a character the source encoding " + charset.name()
                            + " cannot represent",
                    unrepresentable);
        }
    }

    /**
     * A buffer skeleton can only be addressed by a byte span; a node path indexes a tree this format does not
     * have, so reaching here with one is a programming error rather than a data error.
     */
    private static ByteSpanAnchor spanOf(SkeletonAnchor anchor) {
        return switch (anchor) {
            case ByteSpanAnchor span -> span;
            case NodeAnchor ignored ->
                throw new IllegalArgumentException("A buffer skeleton cannot be addressed by a node path");
        };
    }
}
