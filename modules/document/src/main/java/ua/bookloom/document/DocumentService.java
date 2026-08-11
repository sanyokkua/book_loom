package ua.bookloom.document;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.document.epub.EpubReader;
import ua.bookloom.document.epub.EpubWriter;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.Fb2Writer;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.MarkdownWriter;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.DrmRefusedException;
import ua.bookloom.document.model.MalformedFragmentException;
import ua.bookloom.document.txt.TxtReader;
import ua.bookloom.document.txt.TxtWriter;

/**
 * The {@link DocumentPort} implementation: format dispatch plus error classification, and the module's port
 * boundary — every parse and write failure becomes a typed {@link AppError} here, and no exception crosses this
 * class's public methods (09_ERROR_HANDLING.md#boundary-discipline).
 *
 * <p><strong>Why an exhaustive switch and not an injected handler map.</strong> A {@code MapBinder} would turn
 * "you added a format and forgot to bind it" from a compile error into a {@code null} a user discovers.
 * {@link BookFormat} is closed by ADR-0004, so a switch over it makes the compiler the reviewer.
 *
 * <p>{@link #open} routes on the format resolved from the file; {@link #write} routes on
 * {@link Document#format()}, which is authoritative because export is same-format-only (ADR-0004, DD-30).
 *
 * <p><strong>The user-facing messages carry the distinction the error code cannot.</strong> The frozen
 * fifteen-constant error vocabulary has no DRM code, so a protected book and a malformed one both surface as
 * {@link ErrorCode#validation}; what separates them for a user is the message, which is why neither message is
 * allowed to describe the other's situation. The refusal message also names no format and no file-system path —
 * "this is not a valid EPUB" was a wrong diagnosis for three of the four formats, and a path is not on the
 * safe-details allowlist.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class DocumentService implements DocumentPort {

    private final EpubReader epubReader;
    private final EpubWriter epubWriter;
    private final Fb2Reader fb2Reader;
    private final Fb2Writer fb2Writer;
    private final MarkdownReader markdownReader;
    private final MarkdownWriter markdownWriter;
    private final TxtReader txtReader;
    private final TxtWriter txtWriter;

    @Override
    public Result<Document> open(Path source) {
        Objects.requireNonNull(source, "source");
        try {
            return Result.ok(readAs(FormatResolver.resolve(source), source));
        } catch (DrmRefusedException e) {
            return Result.err(drmError(e));
        } catch (CorruptContainerException e) {
            return Result.err(refusalError(e));
        } catch (Throwable t) {
            return Result.err(internalError(t));
        }
    }

    private Document readAs(BookFormat format, Path source) {
        return switch (format) {
            case EPUB -> epubReader.read(source);
            case FB2 -> fb2Reader.read(source);
            case MARKDOWN -> markdownReader.read(source);
            case TXT -> txtReader.read(source);
        };
    }

    @Override
    public Result<Path> write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        try {
            return Result.ok(writeAs(document, destination, targetLanguage));
        } catch (DocumentNotOpenException e) {
            return Result.err(notOpenError(e));
        } catch (CorruptContainerException e) {
            return Result.err(refusalError(e));
        } catch (MalformedFragmentException e) {
            return Result.err(malformedFragmentError(e));
        } catch (Throwable t) {
            return Result.err(internalError(t));
        }
    }

    private Path writeAs(Document document, Path destination, String targetLanguage) {
        return switch (document.format()) {
            case EPUB -> epubWriter.write(document, destination, targetLanguage);
            case FB2 -> fb2Writer.write(document, destination, targetLanguage);
            case MARKDOWN -> markdownWriter.write(document, destination, targetLanguage);
            case TXT -> txtWriter.write(document, destination, targetLanguage);
        };
    }

    private AppError drmError(DrmRefusedException e) {
        log.warn("Refused a protected book", e);
        return AppError.of(
                ErrorCode.validation,
                "This book is protected",
                "This book is protected and cannot be imported. Its content is encrypted, so there is nothing to"
                        + " translate.",
                SafeDetails.empty().render(),
                e);
    }

    private AppError refusalError(CorruptContainerException e) {
        log.warn("Refused a book that could not be read", e);
        return AppError.of(
                ErrorCode.validation,
                "This file could not be opened",
                "This file could not be read as a book — its structure is missing, malformed, or not a format this"
                        + " application supports.",
                SafeDetails.empty().render(),
                e);
    }

    private AppError malformedFragmentError(MalformedFragmentException e) {
        log.warn("Refused a segment whose target text is not well-formed markup", e);
        return AppError.of(
                ErrorCode.validation,
                "This translation could not be saved",
                "One of the translated passages is not well-formed markup and could not be written back into the"
                        + " book. Re-run translation for the affected segment and try saving again.",
                SafeDetails.empty().render(),
                e);
    }

    private AppError notOpenError(DocumentNotOpenException e) {
        log.error("Write requested for a document id that was never opened", e);
        return AppError.of(
                ErrorCode.internal,
                "This book could not be saved",
                "The book is no longer open in this session and must be re-opened before saving.",
                SafeDetails.empty().render(),
                e);
    }

    private AppError internalError(Throwable t) {
        log.error("Unexpected failure in the document port", t);
        return AppError.of(
                ErrorCode.internal,
                "Something went wrong",
                "An unexpected error occurred while processing the book.",
                SafeDetails.empty().render(),
                t);
    }
}
