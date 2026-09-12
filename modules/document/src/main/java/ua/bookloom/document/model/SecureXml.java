package ua.bookloom.document.model;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.SAXHandler;
import org.jspecify.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.ext.EntityResolver2;

/**
 * A {@link SAXBuilder} that expands entity references the way every e-book reader does — from the book's own
 * header, or from the standard named-entity list bundled with the app — and is offline by construction: the one
 * external lookup a book may legitimately need, its DOCTYPE's external subset, is answered from that bundled list
 * whatever URL or scheme the book names, and every other external lookup is answered with nothing. No socket and
 * no file outside the book is ever opened. Not expanding at all was the previous policy, and it made the parser
 * report a reference <em>and</em> its expansion, doubling every non-breaking-space reference on write.
 *
 * <p>An entity whose <em>value</em> is external ({@code <!ENTITY x SYSTEM "file:///…">}) therefore expands to
 * nothing — that is the XXE hole this class exists to close.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecureXml {

    private static final String BUNDLED_ENTITIES = "xhtml-entities.dtd";
    private static final String BUNDLED_SYSTEM_ID = "bookloom:" + BUNDLED_ENTITIES;
    private static final String EMPTY_SYSTEM_ID = "bookloom:empty";

    /**
     * A builder that expands entities, resolves the DOCTYPE's external subset from the bundled list, and empties
     * any other external lookup.
     *
     * @return a freshly configured builder; never shared, because {@link SAXBuilder} is not thread-safe
     */
    public static SAXBuilder builder() {
        final SAXBuilder builder = new SAXBuilder();
        final DoctypeSystemId doctype = new DoctypeSystemId();
        // Every external lookup is allowed to happen precisely because the resolver answers all of them locally.
        // Switching the general-entities feature off instead is what caused the doubling: the JDK's parser then
        // reports a reference AND its expansion even for an entity declared right in the document.
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true);
        builder.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
        builder.setFeature("http://xml.org/sax/features/external-general-entities", true);
        builder.setSAXHandlerFactory(factory -> new DoctypeRecordingHandler(factory, doctype));
        builder.setEntityResolver(new OfflineResolver(doctype));
        return builder;
    }

    /**
     * The system id the document's DOCTYPE names, captured while the header is scanned. The parser asks the
     * resolver for the external subset and for an externally-valued entity through the same call with no name, so
     * this id is the only way to tell the one lookup the bundled list should answer from the ones it must not.
     */
    private static final class DoctypeSystemId {

        private @Nullable String systemId;

        boolean matches(String resolvedSystemId) {
            final String declared = systemId;
            return declared != null && (resolvedSystemId.equals(declared) || resolvedSystemId.endsWith(declared));
        }
    }

    private static final class DoctypeRecordingHandler extends SAXHandler {

        private final DoctypeSystemId doctype;

        DoctypeRecordingHandler(org.jdom2.JDOMFactory factory, DoctypeSystemId doctype) {
            super(factory);
            this.doctype = doctype;
        }

        @Override
        public void startDTD(String name, @Nullable String publicId, @Nullable String systemId)
                throws org.xml.sax.SAXException {
            doctype.systemId = systemId;
            super.startDTD(name, publicId, systemId);
        }
    }

    /**
     * Answers the DOCTYPE's external subset with the bundled entity list — so a book that relies on the FB2 DTD
     * for its named entities still resolves them — and every other external lookup with nothing.
     */
    private static final class OfflineResolver implements EntityResolver2 {

        private final DoctypeSystemId doctype;

        OfflineResolver(DoctypeSystemId doctype) {
            this.doctype = doctype;
        }

        @Override
        public InputSource getExternalSubset(@Nullable String name, @Nullable String baseUri) {
            return bundledEntities();
        }

        @Override
        public InputSource resolveEntity(
                @Nullable String name, @Nullable String publicId, @Nullable String baseUri, String systemId) {
            return doctype.matches(systemId) ? bundledEntities() : empty();
        }

        @Override
        public InputSource resolveEntity(@Nullable String publicId, String systemId) {
            return doctype.matches(systemId) ? bundledEntities() : empty();
        }

        private static InputSource empty() {
            final InputSource source = new InputSource(new StringReader(""));
            source.setSystemId(EMPTY_SYSTEM_ID);
            return source;
        }
    }

    private static InputSource bundledEntities() {
        final InputStream in = Objects.requireNonNull(
                SecureXml.class.getResourceAsStream(BUNDLED_ENTITIES), "bundled entity list is missing");
        final InputSource source = new InputSource(new InputStreamReader(in, StandardCharsets.UTF_8));
        source.setSystemId(BUNDLED_SYSTEM_ID);
        return source;
    }
}
