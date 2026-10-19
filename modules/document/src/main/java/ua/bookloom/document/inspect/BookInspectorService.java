package ua.bookloom.document.inspect;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.epub.EpubInspection;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.FormatResolver;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.ZipEntryReader;
import ua.bookloom.document.txt.TxtInspection;

/**
 * The one public {@link BookInspector} implementation (task 4.2): dispatches to the four per-format inspections by
 * resolved format, and answers {@code UNSUPPORTED} — named from the file's leading bytes — for anything else.
 *
 * <p>{@link #profile(Document)} is not implemented here: assembling a book's title, author, cover, structure tree
 * and statistics is task 4.5's job, once tasks 4.3 and 4.4 exist to feed it. It answers a failed {@code Result}
 * rather than throwing, so no public port method of this class ever lets an exception cross it.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class BookInspectorService implements BookInspector {

    private static final int HEAD_PROBE_BYTES = 256;
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MOBI_MAGIC = "BOOKMOBI".getBytes(StandardCharsets.US_ASCII);
    private static final int MOBI_MAGIC_OFFSET = 60;
    private static final String DOCX_CONTENT_TYPES_ENTRY = "[Content_Types].xml";
    private static final String PDF_TYPE = "PDF";
    private static final String DOCX_TYPE = "DOCX";
    private static final String MOBI_TYPE = "MOBI";
    private static final String UNKNOWN_TYPE = "Unknown";

    private final EpubInspection epubInspection;
    private final Fb2Inspection fb2Inspection;
    private final MarkdownInspection markdownInspection;
    private final TxtInspection txtInspection;

    @Override
    public Result<BookInspection> inspect(Path source) {
        Objects.requireNonNull(source, "source");
        log.debug("Inspecting source={}", source);
        try {
            final BookInspection inspection = FormatResolver.probe(source)
                    .map(format -> inspectAs(format, source))
                    .orElseGet(() -> unsupported(source));
            logOutcome(source, inspection);
            return Result.ok(inspection);
        } catch (Throwable t) {
            final AppError error = internalError(t);
            log.debug("Inspected source={} outcome={}", source, error.code());
            return Result.err(error);
        }
    }

    @Override
    public Result<BookProfile> profile(Document document) {
        Objects.requireNonNull(document, "document");
        log.debug("Profile requested for document id={} format={}", document.id(), document.format());
        try {
            final BookProfile profile = profileOf(document);
            logProfileOutcome(document, profile);
            return Result.ok(profile);
        } catch (DocumentNotOpenException e) {
            final AppError error = notOpenError(e);
            log.debug("Profiled document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        } catch (Throwable t) {
            final AppError error = internalError(t);
            log.debug("Profiled document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        }
    }

    private BookProfile profileOf(Document document) {
        final FormatInspection inspection = inspectionFor(document.format());
        final CoverImage cover = inspection.cover(document).orElse(null);
        final List<StructureNode> structure = inspection.structure(document);
        return new BookProfile(
                document.metadata().get(MetadataKey.TITLE.key()),
                document.metadata().get(MetadataKey.AUTHOR.key()),
                cover,
                structure,
                inspection.stats(document),
                inspection.resourceIds(document));
    }

    private FormatInspection inspectionFor(BookFormat format) {
        return switch (format) {
            case EPUB -> epubInspection;
            case FB2 -> fb2Inspection;
            case MARKDOWN -> markdownInspection;
            case TXT -> txtInspection;
        };
    }

    private void logProfileOutcome(Document document, BookProfile profile) {
        log.info(
                "Profiled document id={} nodeCount={} segments={} words={} coverPresent={}",
                document.id(),
                profile.structure().size(),
                profile.stats().segments(),
                profile.stats().words(),
                profile.cover() != null);
    }

    private AppError notOpenError(DocumentNotOpenException e) {
        log.error("Profile requested for a document id that was never opened", e);
        return AppError.of(
                ErrorCode.internal,
                "This book's profile could not be built",
                "The book is no longer open in this session and must be re-opened before its profile can be built.",
                SafeDetails.empty().render(),
                e);
    }

    private BookInspection inspectAs(BookFormat format, Path source) {
        log.debug("Dispatching inspection source={} format={}", source, format);
        return switch (format) {
            case EPUB -> epubInspection.inspect(source);
            case FB2 -> fb2Inspection.inspect(source);
            case MARKDOWN -> markdownInspection.inspect(source);
            case TXT -> txtInspection.inspect(source);
        };
    }

    private void logOutcome(Path source, BookInspection inspection) {
        log.info(
                "Inspected source={} verdict={} format={} version={} detectedType={} schemePresent={}",
                source,
                inspection.verdict(),
                inspection.format(),
                inspection.formatVersion(),
                inspection.detectedType(),
                inspection.encryptionScheme() != null);
    }

    private BookInspection unsupported(Path source) {
        final byte[] head = readHead(source);
        final String detectedType = detectedTypeOf(source, head);
        log.debug("Unsupported file source={} detectedType={}", source, detectedType);
        return new BookInspection(
                InspectionVerdict.UNSUPPORTED,
                null,
                null,
                detectedType,
                null,
                LanguageEvidenceReader.evaluate(null, List.of()));
    }

    private static String detectedTypeOf(Path source, byte[] head) {
        if (startsWith(head, PDF_MAGIC, 0)) {
            return PDF_TYPE;
        }
        if (startsWith(head, MOBI_MAGIC, MOBI_MAGIC_OFFSET)) {
            return MOBI_TYPE;
        }
        if (startsWith(head, ZIP_MAGIC, 0) && looksLikeDocx(source)) {
            return DOCX_TYPE;
        }
        return UNKNOWN_TYPE;
    }

    private static boolean looksLikeDocx(Path source) {
        try {
            for (final RawEntry entry : ZipEntryReader.readAll(Files.readAllBytes(source))) {
                if (DOCX_CONTENT_TYPES_ENTRY.equals(entry.name())) {
                    return true;
                }
            }
            return false;
        } catch (IOException | CorruptContainerException unreadableZip) {
            return false;
        }
    }

    private static boolean startsWith(byte[] data, byte[] magic, int offset) {
        return data.length >= offset + magic.length
                && Arrays.equals(data, offset, offset + magic.length, magic, 0, magic.length);
    }

    private static byte[] readHead(Path source) {
        try (InputStream in = Files.newInputStream(source)) {
            return in.readNBytes(HEAD_PROBE_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read file for inspection", e);
        }
    }

    private AppError internalError(Throwable t) {
        log.error("Unexpected failure inspecting a candidate file", t);
        return AppError.of(
                ErrorCode.internal,
                "Something went wrong",
                "An unexpected error occurred while inspecting the book.",
                SafeDetails.empty().render(),
                t);
    }
}
