package ua.bookloom.document.fb2;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.Jdom2TreeNode;
import ua.bookloom.document.model.SkeletonAnchors;

/**
 * Reassembles an FB2 book previously opened by {@link Fb2Reader} and writes it back in its original container.
 *
 * <p><strong>The output encoding is decided over the real output, not predicted.</strong> A {@code windows-1251}
 * book translated into Ukrainian is fully representable right up until one em-dash or curly quote appears — and
 * that character is never the one anybody predicts. So the reassembled tree is serialized first, then the result
 * is offered to the declared charset's encoder with {@link CodingErrorAction#REPORT}: it either encodes, and the
 * declaration is kept exactly as the source spelled it, or it does not, and the document is written UTF-8 with a
 * rewritten declaration.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class Fb2Writer {

    private static final String TITLE_INFO = "title-info";
    private static final String LANG = "lang";

    private final OpenFb2Registry registry;

    /**
     * Reassembles {@code document} and writes it to {@code destination}, setting the target language.
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage the language to declare in {@code title-info}
     * @return {@code destination}
     * @throws DocumentNotOpenException if {@code document}'s id was never registered by {@link Fb2Reader#read}
     */
    public Path write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final ParsedFb2 parsed =
                registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));

        writeSegmentsBack(document, parsed);
        setTargetLanguage(parsed, targetLanguage);
        writeBytes(destination, serialize(parsed), parsed);
        return destination;
    }

    /**
     * Writes every accepted segment's target content back before anything is serialized, so no write can perturb
     * resolving a later segment's anchor.
     *
     * <p>A unit's writes are handed over as one batch rather than applied one at a time, because a translation
     * that legitimately reorders inline markup can move a line break to a block's top level and change how that
     * block splits into runs — see {@link SkeletonAnchors} for the measured case.
     */
    private static void writeSegmentsBack(Document document, ParsedFb2 parsed) {
        for (final Unit unit : document.units()) {
            SkeletonAnchors.writeBackAll(Jdom2TreeNode.of(bodyFor(parsed, unit)), pendingWrites(unit));
        }
    }

    private static List<SkeletonAnchors.PendingWrite> pendingWrites(Unit unit) {
        final List<SkeletonAnchors.PendingWrite> writes = new ArrayList<>();
        for (final Segment segment : unit.segments()) {
            final String targetInner = segment.targetInner();
            if (targetInner != null) {
                writes.add(new SkeletonAnchors.PendingWrite(segment.anchor(), targetInner));
            }
        }
        return writes;
    }

    private static Element bodyFor(ParsedFb2 parsed, Unit unit) {
        final Element body = parsed.bodiesByHandleId().get(unit.skeleton().opaqueId());
        return Objects.requireNonNull(body, () -> "No parsed body registered for unit " + unit.id());
    }

    /**
     * Replaces the first {@code <lang>} inside {@code title-info}, adding one when none is present, and leaves
     * {@code src-lang} and any {@code src-title-info} block alone. FB2 records two languages — what the book is,
     * and what it was translated from — and overwriting the second destroys the only remaining record that the
     * book started out in another language, which the translated file cannot recover.
     */
    private static void setTargetLanguage(ParsedFb2 parsed, String targetLanguage) {
        final Element titleInfo =
                Fb2Metadata.childOf(Fb2Metadata.childOf(parsed.document().getRootElement(), "description"), TITLE_INFO);
        if (titleInfo == null) {
            return;
        }
        final Element existing = Fb2Metadata.childOf(titleInfo, LANG);
        if (existing == null) {
            titleInfo.addContent(new Element(LANG, titleInfo.getNamespace()).setText(targetLanguage));
        } else {
            existing.setText(targetLanguage);
        }
    }

    /**
     * Serializes and decides the encoding in one step, because the decision is a property of the serialized text.
     */
    private static Serialized serialize(ParsedFb2 parsed) {
        // Raw format is not "the bytes as read": its line separator defaults to \r\n (decision debt D11), so an
        // LF source would come back with every line ending doubled. Echo the style the source used.
        final Format format = Format.getRawFormat();
        format.setLineSeparator(parsed.crlfLineEndings() ? LineSeparator.CRNL : LineSeparator.NL);
        final String xml = new XMLOutputter(format).outputString(parsed.document());
        final Charset declared = parsed.charset();
        if (isRepresentable(xml, declared)) {
            return new Serialized(xml, declared, encodingNameToDeclare(parsed, declared));
        }
        return new Serialized(xml, StandardCharsets.UTF_8, StandardCharsets.UTF_8.name());
    }

    /**
     * Echoes the encoding name exactly as the source spelled it when it is being kept — one corpus file writes
     * {@code utf-8} lower-case, and re-spelling a preserved declaration is a change the round trip did not ask
     * for.
     */
    private static String encodingNameToDeclare(ParsedFb2 parsed, Charset declared) {
        final String asWritten = parsed.declaredEncodingName();
        return asWritten == null ? declared.name() : asWritten;
    }

    private static boolean isRepresentable(String xml, Charset charset) {
        if (!charset.canEncode()) {
            return false;
        }
        final CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            encoder.encode(CharBuffer.wrap(xml));
            return true;
        } catch (CharacterCodingException unrepresentable) {
            return false;
        }
    }

    private static void writeBytes(Path destination, Serialized serialized, ParsedFb2 parsed) {
        final byte[] fb2Bytes = renderWithDeclaration(serialized);
        try {
            if (parsed.zipMemberName() == null) {
                Files.write(destination, fb2Bytes);
            } else {
                writeZip(destination, parsed.zipMemberName(), fb2Bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write FictionBook output", e);
        }
    }

    /**
     * Re-emits the document with a declaration naming the encoding actually used. JDOM's own outputter is asked
     * for the body only, so the declaration is written here where the chosen encoding is known — the alternative,
     * letting the outputter emit it, would spell a preserved encoding as this JVM normalises it rather than as
     * the source wrote it.
     */
    private static byte[] renderWithDeclaration(Serialized serialized) {
        final String withoutDeclaration = stripDeclaration(serialized.xml());
        final String declaration = "<?xml version=\"1.0\" encoding=\"" + serialized.declaredName() + "\"?>";
        return (declaration + withoutDeclaration).getBytes(serialized.charset());
    }

    private static String stripDeclaration(String xml) {
        if (!xml.startsWith("<?xml")) {
            return xml;
        }
        final int end = xml.indexOf("?>");
        return end < 0 ? xml : xml.substring(end + 2);
    }

    /**
     * Re-zips a book imported as {@code .fb2.zip} under the member name it arrived with — export re-emits the
     * container it was given, so a user who imported a zipped book expects one back.
     */
    private static void writeZip(Path destination, String memberName, byte[] fb2Bytes) throws IOException {
        try (OutputStream out = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(memberName));
            zip.write(fb2Bytes);
            zip.closeEntry();
        }
    }

    /**
     * The serialized book plus the encoding decision taken over it.
     *
     * @param xml the reassembled document as text, declaration included
     * @param charset the encoding the bytes will actually be written in
     * @param declaredName the encoding name to write into the declaration — the source's own spelling when the
     *     declaration is being kept, and the canonical UTF-8 name when it is being rewritten
     */
    private record Serialized(String xml, Charset charset, String declaredName) {}
}
