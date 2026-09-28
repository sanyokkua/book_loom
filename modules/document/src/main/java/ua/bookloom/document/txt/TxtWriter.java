package ua.bookloom.document.txt;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.document.AttributeAnchor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.BufferTextWriter;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * Writes a plain-text book back by copying the original buffer and substituting only translated spans.
 *
 * <p><strong>The target language is accepted and ignored, deliberately.</strong> Plain text has nowhere to record
 * one. Inventing a place — a header line — would add content the source never had and break the byte-exact round
 * trip for a field nothing reads.
 *
 * <p><strong>An unrepresentable character switches the whole file to UTF-8.</strong> Plain text has no declaration
 * to rewrite, so the file is written whole in UTF-8 with the byte-order mark exactly when the source had one, and
 * the application's own re-open reads it as UTF-8 (ADR-0029). A translation the source's code page cannot hold is
 * never refused and never written as {@code ?}.
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
     */
    public Path write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final OpenTxtRegistry.ParsedTxt parsed =
                registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));

        final byte[] output = BufferTextWriter.assemble(
                parsed.originalBytes(),
                parsed.charset(),
                Boolean.TRUE.equals(document.hasBom()),
                replacementsOf(document),
                document.id(),
                document.format());
        try {
            Files.write(destination, output);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write plain-text output", e);
        }
        return destination;
    }

    private static List<BufferTextWriter.TextReplacement> replacementsOf(Document document) {
        final List<BufferTextWriter.TextReplacement> replacements = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary()) {
                continue;
            }
            for (final Segment segment : unit.segments()) {
                final String targetInner = segment.targetInner();
                if (targetInner != null) {
                    replacements.add(new BufferTextWriter.TextReplacement(spanOf(segment.anchor()), targetInner));
                }
            }
        }
        return replacements;
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
            case AttributeAnchor ignored ->
                throw new IllegalArgumentException("A buffer skeleton cannot be addressed by an attribute");
        };
    }
}
