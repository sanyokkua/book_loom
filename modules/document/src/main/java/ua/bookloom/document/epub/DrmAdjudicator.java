package ua.bookloom.document.epub;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.model.DrmRefusedException;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SecureXml;

/**
 * Decides whether {@code META-INF/encryption.xml} describes DRM or ordinary font obfuscation, from
 * <strong>what was encrypted</strong> rather than from the algorithm's name (ADR-0026).
 *
 * <p><strong>Why the algorithm URI no longer gates the decision.</strong> A survey of 194 real books found 30
 * carrying an encryption manifest, none of them DRM, and 99 of 99 {@code CipherReference} targets were font
 * files. The Adobe algorithm appears in the wild without the trailing {@code 4} the specification names, so an
 * exact-match allowlist refused 16 of 194 books — 8% of an ordinary library, every one a normal book whose only
 * encrypted resources were obfuscated fonts. The check was blind in the other direction too: inspecting only the
 * algorithm, it would have <em>allowed</em> a book that encrypted its content documents under a font-obfuscation
 * URI, which is the exact failure EC-EPUB-1 exists to prevent. The algorithm is now diagnostic only.
 *
 * <p><strong>Two path bases, deliberately not one helper.</strong> A {@code CipherReference/@URI} resolves
 * against the <em>container root</em>; a manifest {@code href} resolves against the <em>OPF directory</em>. All
 * 89 unambiguous corpus cases resolve root-relative and none resolve OPF-relative, and 102 of 194 books have a
 * non-root OPF — so one shared resolver would silently mis-resolve the majority of books.
 *
 * <p>This runs after the OPF is parsed, because it needs the manifest, but before any content document is read
 * and before any {@code Document}, {@code Unit} or {@code Segment} exists. Parsing an OPF into memory is not a
 * partial import, so EC-EPUB-1's "no partial import" guarantee is preserved (design.md D11).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DrmAdjudicator {

    private static final Namespace ENC_NS = Namespace.getNamespace("http://www.w3.org/2001/04/xmlenc#");
    private static final int PERCENT_ESCAPE_LENGTH = 3;
    private static final int HEX_RADIX = 16;

    /**
     * Adjudicates the book's encryption manifest against its package manifest.
     *
     * @param encryptionEntry {@code META-INF/encryption.xml}'s raw entry, or {@code null} if the archive has none
     * @param opf the already-parsed OPF, needed for its manifest media types
     * @throws DrmRefusedException if any encrypted resource is not a manifest-declared font, if a cipher
     *     reference resolves to no manifest item, or if the encryption manifest is malformed
     */
    static void adjudicate(@Nullable RawEntry encryptionEntry, ParsedOpf opf) {
        if (encryptionEntry == null) {
            return;
        }
        final Map<String, String> mediaTypesByPath = manifestMediaTypesByContainerPath(opf);
        for (final String cipherPath : encryptedResourcePaths(encryptionEntry)) {
            refuseUnlessFont(cipherPath, mediaTypesByPath.get(cipherPath));
        }
    }

    private static void refuseUnlessFont(String cipherPath, @Nullable String mediaType) {
        if (mediaType == null) {
            throw new DrmRefusedException("Encrypted resource is not declared in the package manifest: " + cipherPath);
        }
        if (!FontMediaTypes.isFont(mediaType)) {
            throw new DrmRefusedException(
                    "Encrypted resource is content rather than a font: " + cipherPath + " (" + mediaType + ")");
        }
    }

    /** Every {@code CipherReference/@URI}, resolved against the <strong>container root</strong>. */
    private static List<String> encryptedResourcePaths(RawEntry encryptionEntry) {
        final Element root = parse(encryptionEntry.content()).getRootElement();
        final List<String> paths = new ArrayList<>();
        for (final Element encryptedData : root.getChildren("EncryptedData", ENC_NS)) {
            paths.add(resolveAgainstContainerRoot(cipherUriOf(encryptedData)));
        }
        return paths;
    }

    private static String cipherUriOf(Element encryptedData) {
        final Element cipherData = encryptedData.getChild("CipherData", ENC_NS);
        final Element reference = cipherData == null ? null : cipherData.getChild("CipherReference", ENC_NS);
        final String uri = reference == null ? null : reference.getAttributeValue("URI");
        if (uri == null || uri.isBlank()) {
            throw new DrmRefusedException("Encryption manifest declares encrypted data with no cipher reference");
        }
        return uri;
    }

    /**
     * A cipher URI is already relative to the container root; only a leading slash and percent-escapes stand
     * between it and an entry name. Decoding matters because a font whose file name contains a space would
     * otherwise fail to match its own manifest item and refuse a book that has no DRM at all — the precise
     * failure mode ADR-0026 exists to remove.
     */
    private static String resolveAgainstContainerRoot(String cipherUri) {
        final String withoutLeadingSlash = cipherUri.startsWith("/") ? cipherUri.substring(1) : cipherUri;
        return percentDecode(withoutLeadingSlash);
    }

    /** Manifest {@code href}s, resolved against the <strong>OPF directory</strong>, mapped to their media types. */
    private static Map<String, String> manifestMediaTypesByContainerPath(ParsedOpf opf) {
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        final Map<String, String> byPath = new LinkedHashMap<>();
        for (final ManifestItem item : opf.manifestItems()) {
            byPath.put(percentDecode(OpfPaths.resolve(opfDir, item.href())), item.mediaType());
        }
        return byPath;
    }

    /**
     * Decodes {@code %XX} escapes only. {@link java.net.URLDecoder} is deliberately not used: it also turns
     * {@code +} into a space, which is right for form encoding and wrong for a path.
     */
    private static String percentDecode(String path) {
        if (path.indexOf('%') < 0) {
            return path;
        }
        final ByteArrayOutputStream decoded = new ByteArrayOutputStream(path.length());
        int index = 0;
        while (index < path.length()) {
            index = decodeOneAt(path, index, decoded);
        }
        return decoded.toString(StandardCharsets.UTF_8);
    }

    private static int decodeOneAt(String path, int index, ByteArrayOutputStream decoded) {
        final char current = path.charAt(index);
        if (current == '%' && index + PERCENT_ESCAPE_LENGTH <= path.length()) {
            return decodeEscapeAt(path, index, decoded);
        }
        decoded.writeBytes(String.valueOf(current).getBytes(StandardCharsets.UTF_8));
        return index + 1;
    }

    private static int decodeEscapeAt(String path, int index, ByteArrayOutputStream decoded) {
        try {
            decoded.write(Integer.parseInt(path.substring(index + 1, index + PERCENT_ESCAPE_LENGTH), HEX_RADIX));
            return index + PERCENT_ESCAPE_LENGTH;
        } catch (NumberFormatException malformedEscape) {
            decoded.write('%');
            return index + 1;
        }
    }

    /**
     * A malformed encryption manifest refuses the book rather than reporting a corrupt container: the archive
     * states that something in it is encrypted and we cannot establish what, so the check fails closed
     * (ADR-0026).
     */
    private static org.jdom2.Document parse(byte[] content) {
        try {
            return SecureXml.builder().build(new ByteArrayInputStream(content));
        } catch (JDOMException | IOException e) {
            throw new DrmRefusedException("Encryption manifest is malformed and cannot be adjudicated", e);
        }
    }
}
