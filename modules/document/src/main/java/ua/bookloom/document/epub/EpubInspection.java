package ua.bookloom.document.epub;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.inspect.FormatInspection;
import ua.bookloom.document.inspect.LanguageEvidenceReader;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.TreeNode;
import ua.bookloom.document.model.ZipEntryReader;

/**
 * EPUB's own {@link FormatInspection} (task 4.2): readable with the package version, DRM-protected with the
 * encryption scheme where one can be named, or — for a container too damaged to inspect — readable with no
 * version, so {@code DocumentPort#open} is left to give the real reason.
 *
 * <p>Deliberately does not call {@link DrmAdjudicator#adjudicate} or {@code EpubReader#read}: adjudication throws,
 * which an inspection must never do (ADR-0039), and a full read builds every spine unit's segments, which a
 * preview does not need.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EpubInspection implements FormatInspection {

    private static final String CONTAINER_ENTRY = "META-INF/container.xml";
    private static final String ENCRYPTION_ENTRY = "META-INF/encryption.xml";
    private static final String EPUB_TYPE = "EPUB";
    private static final String VERSION_PREFIX = "EPUB ";
    private static final String COVER_IMAGE_PROPERTY = "cover-image";
    private static final String COVER_GUIDE_TYPE = "cover";
    private static final String IMG_TAG = "img";
    private static final String SRC_ATTRIBUTE = "src";
    private static final String DEFAULT_IMAGE_MEDIA_TYPE = "application/octet-stream";

    private final OpenEpubRegistry registry;

    @Override
    public BookInspection inspect(Path source) {
        Objects.requireNonNull(source, "source");
        try {
            return inspectReadableContainer(source);
        } catch (CorruptContainerException unreadable) {
            log.debug("EPUB container unreadable for inspection source={} reason={}", source, unreadable.getMessage());
            return readableNoVersion();
        }
    }

    private BookInspection inspectReadableContainer(Path source) {
        final byte[] fileBytes = readAllBytes(source);
        final Map<String, RawEntry> byName = index(ZipEntryReader.readAll(fileBytes));
        final String opfPath = ContainerReader.locateOpf(byName.get(CONTAINER_ENTRY));
        final RawEntry opfEntry = requireEntry(byName, opfPath);
        final ParsedOpf opf = OpfParser.parse(opfEntry.content(), opfPath);

        final EncryptionFinding finding = DrmAdjudicator.probeEncryption(byName.get(ENCRYPTION_ENTRY), opf, byName);
        final LanguageEvidence evidence = languageEvidenceOf(opf, byName, finding.contentEncrypted());
        if (finding.contentEncrypted()) {
            log.debug("EPUB content encrypted source={} scheme={}", source, finding.scheme());
            return new BookInspection(
                    InspectionVerdict.DRM_PROTECTED, BookFormat.EPUB, null, EPUB_TYPE, finding.scheme(), evidence);
        }
        final String version = opf.version() == null ? null : VERSION_PREFIX + opf.version();
        log.debug("EPUB package version source={} version={}", source, version);
        return new BookInspection(InspectionVerdict.READABLE, BookFormat.EPUB, version, EPUB_TYPE, null, evidence);
    }

    /**
     * Content documents are not parsed for their root language declaration once content is known to be encrypted:
     * their bytes are ciphertext, not XHTML, and the declared package language is the only evidence there is.
     */
    private LanguageEvidence languageEvidenceOf(ParsedOpf opf, Map<String, RawEntry> byName, boolean contentEncrypted) {
        final String declaredRaw =
                opf.dcLanguages().isEmpty() ? null : opf.dcLanguages().get(0);
        final List<@Nullable String> declarations = contentEncrypted ? List.of() : rootDeclarations(opf, byName);
        return LanguageEvidenceReader.evaluate(declaredRaw, declarations);
    }

    /**
     * Each present spine document's own root-tag declaration — {@code xml:lang}, else {@code lang}. A spine item
     * whose file is absent from the archive is skipped, matching {@code EpubReader}'s own tolerant read.
     */
    private static List<@Nullable String> rootDeclarations(ParsedOpf opf, Map<String, RawEntry> byName) {
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        final List<@Nullable String> declarations = new ArrayList<>();
        for (final SpineItem item : opf.spineItems()) {
            final RawEntry entry = byName.get(OpfPaths.resolve(opfDir, item.href()));
            if (entry != null) {
                declarations.add(rootDeclaredLanguage(XhtmlParser.parse(entry.content(), item.href())));
            }
        }
        return declarations;
    }

    private static @Nullable String rootDeclaredLanguage(org.jsoup.nodes.Document doc) {
        final Element html = doc.selectFirst("html");
        if (html == null) {
            return null;
        }
        final TreeNode root = JsoupTreeNode.of(html);
        final String xmlLang = root.attribute("xml:lang");
        return xmlLang != null ? xmlLang : root.attribute("lang");
    }

    private static BookInspection readableNoVersion() {
        return new BookInspection(
                InspectionVerdict.READABLE,
                BookFormat.EPUB,
                null,
                EPUB_TYPE,
                null,
                LanguageEvidenceReader.evaluate(null, List.of()));
    }

    private static Map<String, RawEntry> index(List<RawEntry> entries) {
        final Map<String, RawEntry> byName = new LinkedHashMap<>();
        for (final RawEntry entry : entries) {
            byName.put(entry.name(), entry);
        }
        return byName;
    }

    private static RawEntry requireEntry(Map<String, RawEntry> byName, String name) {
        final RawEntry entry = byName.get(name);
        if (entry == null) {
            throw new CorruptContainerException("Missing archive entry: " + name);
        }
        return entry;
    }

    /**
     * A genuine I/O fault reading the file is not a verdict this inspection can answer — it propagates so
     * {@code BookInspectorService}'s boundary turns it into a failed {@code Result}, distinct from a malformed
     * container's {@code READABLE}-with-no-version verdict.
     */
    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read EPUB file for inspection", e);
        }
    }

    /**
     * Extracts this EPUB's cover image by the first of four historical rules that resolves one (task 4.3): the
     * EPUB 3 manifest item marked {@code cover-image}, the EPUB 2 {@code <meta name="cover">} read as a manifest
     * id or else as an href, or the first image of the guide's {@code type="cover"} document — read from the
     * registry-held state {@link EpubReader} parsed. A rule naming a resource this archive does not actually
     * contain falls through to the next rule rather than reporting a failure.
     */
    @Override
    public Optional<CoverImage> cover(Document document) {
        Objects.requireNonNull(document, "document");
        final Optional<ParsedEpub> parsed = registry.find(document.id());
        if (parsed.isEmpty()) {
            log.debug("No open EPUB state for cover lookup documentId={}", document.id());
            return Optional.empty();
        }
        return coverOf(parsed.get());
    }

    private Optional<CoverImage> coverOf(ParsedEpub parsed) {
        final Map<String, RawEntry> byName = index(parsed.rawEntries());
        final RawEntry opfEntry = byName.get(parsed.opfPath());
        if (opfEntry == null) {
            log.debug("OPF entry missing from raw entries for cover lookup opfPath={}", parsed.opfPath());
            return Optional.empty();
        }
        final ParsedOpf opf = OpfParser.parse(opfEntry.content(), parsed.opfPath());
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        return coverImagePropertyRule(opf, byName, opfDir)
                .or(() -> coverMetaAsIdRule(opf, byName, opfDir))
                .or(() -> coverMetaAsHrefRule(opf, byName, opfDir))
                .or(() -> guideCoverRule(opf, byName, opfDir));
    }

    private Optional<CoverImage> coverImagePropertyRule(ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        for (final ManifestItem item : opf.manifestItems()) {
            if (item.properties().contains(COVER_IMAGE_PROPERTY)) {
                final Optional<CoverImage> cover = coverImageFrom(item.href(), item.mediaType(), byName, opfDir);
                logRuleOutcome("EPUB3 cover-image property", item.href(), cover);
                if (cover.isPresent()) {
                    return cover;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<CoverImage> coverMetaAsIdRule(ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        final String metaContent = opf.coverMetaContent();
        if (metaContent == null) {
            return Optional.empty();
        }
        for (final ManifestItem item : opf.manifestItems()) {
            if (item.id().equals(metaContent)) {
                final Optional<CoverImage> cover = coverImageFrom(item.href(), item.mediaType(), byName, opfDir);
                logRuleOutcome("EPUB2 cover meta as manifest id", item.href(), cover);
                return cover;
            }
        }
        log.debug("EPUB2 cover meta content matches no manifest id content={}; falling through", metaContent);
        return Optional.empty();
    }

    private Optional<CoverImage> coverMetaAsHrefRule(ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        final String metaContent = opf.coverMetaContent();
        if (metaContent == null) {
            return Optional.empty();
        }
        final Optional<CoverImage> cover =
                coverImageFrom(metaContent, mediaTypeOfHref(metaContent, opf), byName, opfDir);
        logRuleOutcome("EPUB2 cover meta as href", metaContent, cover);
        return cover;
    }

    private Optional<CoverImage> guideCoverRule(ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        for (final GuideReference reference : opf.guideReferences()) {
            if (COVER_GUIDE_TYPE.equals(reference.type())) {
                final Optional<CoverImage> cover = coverFromGuideDocument(reference.href(), opf, byName, opfDir);
                logRuleOutcome("EPUB2 guide cover document", reference.href(), cover);
                if (cover.isPresent()) {
                    return cover;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The guide's own {@code type="cover"} document's first {@code <img>}, resolved against that document's own
     * directory — never the OPF's — since the image's {@code src} is relative to the document that declares it.
     */
    private Optional<CoverImage> coverFromGuideDocument(
            String guideHref, ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        final String resolvedDocPath = OpfPaths.resolve(opfDir, guideHref);
        final RawEntry docEntry = byName.get(resolvedDocPath);
        if (docEntry == null) {
            return Optional.empty();
        }
        final org.jsoup.nodes.Document guideDoc = XhtmlParser.parse(docEntry.content(), resolvedDocPath);
        final Element img = guideDoc.selectFirst(IMG_TAG);
        if (img == null) {
            return Optional.empty();
        }
        final String src = img.attr(SRC_ATTRIBUTE);
        if (src.isEmpty()) {
            return Optional.empty();
        }
        final String docDir = OpfPaths.parentOf(resolvedDocPath);
        return coverImageFrom(src, mediaTypeOfHref(src, opf), byName, docDir);
    }

    /**
     * Resolves {@code href} against {@code baseDir} to find the archive entry it names, reporting the cover under
     * {@code href} exactly as declared — never the archive-absolute path — so a person reading the profile sees the
     * name the book itself used.
     */
    private static Optional<CoverImage> coverImageFrom(
            String href, String mediaType, Map<String, RawEntry> byName, String baseDir) {
        final RawEntry entry = byName.get(OpfPaths.resolve(baseDir, href));
        return entry == null ? Optional.empty() : Optional.of(new CoverImage(href, mediaType, entry.content()));
    }

    /**
     * The declared media type of a resource named only by its {@code href} — an EPUB 2 {@code <meta>}-as-href or a
     * guide document's {@code <img src>} — found by matching a manifest item that declares the same href, since
     * neither of those references carries a media type of its own.
     */
    private static String mediaTypeOfHref(String href, ParsedOpf opf) {
        for (final ManifestItem item : opf.manifestItems()) {
            if (item.href().equals(href)) {
                return item.mediaType();
            }
        }
        return DEFAULT_IMAGE_MEDIA_TYPE;
    }

    /**
     * Builds this EPUB's structure tree from its own navigation (task 4.4): the nav document's table of contents,
     * or the NCX where there is none, mapped onto the spine units {@link EpubStructureBuilder} resolves — read
     * from the registry-held state {@link EpubReader} parsed, same as {@link #cover(Document)}.
     */
    @Override
    public List<StructureNode> structure(Document document) {
        Objects.requireNonNull(document, "document");
        final Optional<ParsedEpub> parsed = registry.find(document.id());
        if (parsed.isEmpty()) {
            log.debug("No open EPUB state for structure lookup documentId={}", document.id());
            return List.of();
        }
        return structureOf(parsed.get(), document);
    }

    private List<StructureNode> structureOf(ParsedEpub parsed, Document document) {
        final Map<String, RawEntry> byName = index(parsed.rawEntries());
        final RawEntry opfEntry = byName.get(parsed.opfPath());
        if (opfEntry == null) {
            log.debug("OPF entry missing from raw entries for structure lookup opfPath={}", parsed.opfPath());
            return List.of();
        }
        final ParsedOpf opf = OpfParser.parse(opfEntry.content(), parsed.opfPath());
        final NavigationParser.Result navigation = NavigationParser.parse(opf, byName);
        log.debug(
                "EPUB structure navigation source={} topLevelEntries={}",
                navigation.source(),
                navigation.entries().size());
        return EpubStructureBuilder.build(navigation, document);
    }

    /**
     * Computes this EPUB's statistics (task 4.5) from the registry-held state {@link EpubReader} parsed, the same
     * source {@link #cover(Document)} and {@link #structure(Document)} read.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public BookStats stats(Document document) {
        Objects.requireNonNull(document, "document");
        final ParsedEpub parsed = requireOpen(document);
        final BookStats stats = EpubStats.compute(document, parsed, opfOf(parsed));
        log.debug(
                "EPUB stats documentId={} segments={} words={} images={} codeBlocks={} fonts={} tables={} footnotes={}",
                document.id(),
                stats.segments(),
                stats.words(),
                stats.images(),
                stats.codeBlocks(),
                stats.fonts(),
                stats.tables(),
                stats.footnotes());
        return stats;
    }

    /**
     * Collects every resource id this EPUB carries (task 4.5): its image and font manifest ids, plus every id an
     * internal link fragment targets anywhere in its spine content.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public Set<String> resourceIds(Document document) {
        Objects.requireNonNull(document, "document");
        final ParsedEpub parsed = requireOpen(document);
        final Set<String> ids = EpubStats.resourceIds(document, parsed, opfOf(parsed));
        log.debug("EPUB resource ids documentId={} count={}", document.id(), ids.size());
        return ids;
    }

    private @Nullable ParsedOpf opfOf(ParsedEpub parsed) {
        final Map<String, RawEntry> byName = index(parsed.rawEntries());
        final RawEntry opfEntry = byName.get(parsed.opfPath());
        if (opfEntry == null) {
            log.debug("OPF entry missing from raw entries opfPath={}", parsed.opfPath());
            return null;
        }
        return OpfParser.parse(opfEntry.content(), parsed.opfPath());
    }

    private ParsedEpub requireOpen(Document document) {
        return registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));
    }

    private void logRuleOutcome(String rule, String resourcePath, Optional<CoverImage> cover) {
        if (cover.isPresent()) {
            log.debug("Cover matched rule={} resourcePath={}", rule, resourcePath);
        } else {
            log.debug("Cover rule fell through rule={} resourcePath={}", rule, resourcePath);
        }
    }
}
