package ua.bookloom.document;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.epub.EpubReader;
import ua.bookloom.document.epub.EpubWriter;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.Fb2Writer;
import ua.bookloom.document.mask.GateOutcome;
import ua.bookloom.document.mask.PlaceholderGate;
import ua.bookloom.document.mask.RestoredContent;
import ua.bookloom.document.mask.Unmasker;
import ua.bookloom.document.md.MarkdownEscaper;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.MarkdownStructureCheck;
import ua.bookloom.document.md.MarkdownWriter;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.DrmRefusedException;
import ua.bookloom.document.model.MalformedFragmentException;
import ua.bookloom.document.model.XmlCharacters;
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
        log.debug("Opening document source={}", source);
        @Nullable BookFormat resolvedFormat = null;
        try {
            final BookFormat format = FormatResolver.resolve(source);
            resolvedFormat = format;
            log.debug("Resolved document source={} format={}", source, format);
            final Document document = readAs(format, source);
            log.debug("Opened document source={} format={} outcome=success", source, format);
            return Result.ok(document);
        } catch (DrmRefusedException e) {
            final AppError error = drmError(e);
            logOpenFailure(source, resolvedFormat, error);
            return Result.err(error);
        } catch (CorruptContainerException e) {
            final AppError error = refusalError(e);
            logOpenFailure(source, resolvedFormat, error);
            return Result.err(error);
        } catch (Throwable t) {
            final AppError error = internalError(t);
            logOpenFailure(source, resolvedFormat, error);
            return Result.err(error);
        }
    }

    private void logOpenFailure(Path source, @Nullable BookFormat resolvedFormat, AppError error) {
        log.debug(
                "Opened document source={} format={} outcome={}",
                source,
                Objects.toString(resolvedFormat, "unresolved"),
                error.code());
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
        logWriteEntry(document, destination, targetLanguage);
        try {
            final Path written = writeAs(document, destination, targetLanguage);
            log.debug("Wrote document id={} format={} outcome=success", document.id(), document.format());
            return Result.ok(written);
        } catch (DocumentNotOpenException e) {
            final AppError error = notOpenError(e);
            log.debug("Wrote document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        } catch (CorruptContainerException e) {
            final AppError error = refusalError(e);
            log.debug("Wrote document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        } catch (MalformedFragmentException e) {
            final AppError error = malformedFragmentError(e);
            log.debug("Wrote document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        } catch (Throwable t) {
            final AppError error = internalError(t);
            log.debug("Wrote document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        }
    }

    private void logWriteEntry(Document document, Path destination, String targetLanguage) {
        log.debug(
                "Writing document id={} format={} destination={} targetLanguage={} translatedSegments={}",
                document.id(),
                document.format(),
                destination,
                targetLanguage,
                translatedSegmentCount(document));
    }

    @Override
    public Result<Boolean> close(Document document) {
        Objects.requireNonNull(document, "document");
        log.debug("Closing document id={} format={}", document.id(), document.format());
        try {
            final boolean wasOpen = closeAs(document);
            log.debug("Closed document id={} format={} wasOpen={}", document.id(), document.format(), wasOpen);
            return Result.ok(wasOpen);
        } catch (Throwable t) {
            final AppError error = internalError(t);
            log.debug("Closed document id={} format={} outcome={}", document.id(), document.format(), error.code());
            return Result.err(error);
        }
    }

    private boolean closeAs(Document document) {
        return switch (document.format()) {
            case EPUB -> epubReader.close(document.id());
            case FB2 -> fb2Reader.close(document.id());
            case MARKDOWN -> markdownReader.close(document.id());
            case TXT -> txtReader.close(document.id());
        };
    }

    private Path writeAs(Document document, Path destination, String targetLanguage) {
        return switch (document.format()) {
            case EPUB -> epubWriter.write(document, destination, targetLanguage);
            case FB2 -> fb2Writer.write(document, destination, targetLanguage);
            case MARKDOWN -> markdownWriter.write(document, destination, targetLanguage);
            case TXT -> txtWriter.write(document, destination, targetLanguage);
        };
    }

    @Override
    public Result<String> unmask(BookFormat format, Segment segment, String translatedMasked) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(translatedMasked, "translatedMasked");
        logUnmaskEntry(format, segment, translatedMasked);
        try {
            final GateOutcome outcome = PlaceholderGate.compare(segment.masked(), translatedMasked);
            if (!outcome.matches()) {
                final AppError error = gateError(outcome);
                log.debug("Unmasked segment format={} segmentId={} outcome={}", format, segment.id(), error.code());
                return Result.err(error);
            }
            final RestoredContent restored = Unmasker.restore(format, segment, translatedMasked);
            log.trace("Unmask restored format={} segmentId={} restoredText={}", format, segment.id(), restored.text());
            final Result<String> result =
                    switch (format) {
                        case MARKDOWN -> restoreMarkdown(segment, restored);
                        case EPUB, FB2 -> restoreTree(restored);
                        case TXT -> Result.ok(restored.text());
                    };
            logUnmaskOutcome(format, segment.id(), result);
            return result;
        } catch (Throwable t) {
            final AppError error = internalError(t);
            log.debug("Unmasked segment format={} segmentId={} outcome={}", format, segment.id(), error.code());
            return Result.err(error);
        }
    }

    private void logUnmaskEntry(BookFormat format, Segment segment, String translatedMasked) {
        log.debug(
                "Unmasking segment format={} segmentId={} placeholders={}",
                format,
                segment.id(),
                segment.placeholders().size());
        log.trace("Unmask input format={} segmentId={} maskedInput={}", format, segment.id(), translatedMasked);
    }

    private static long translatedSegmentCount(Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.targetInner() != null)
                .count();
    }

    private void logUnmaskOutcome(BookFormat format, String segmentId, Result<String> result) {
        if (result.isOk()) {
            log.debug("Unmasked segment format={} segmentId={} outcome=success", format, segmentId);
            return;
        }
        log.debug(
                "Unmasked segment format={} segmentId={} outcome={}",
                format,
                segmentId,
                Objects.requireNonNull(result.error(), "error").code());
    }

    /**
     * The tree-format tail of restore: refuse content carrying a character XML 1.0 cannot represent, so the
     * segment fails here rather than in the writer. {@link XmlCharacters} carries the measured reason — FB2 threw
     * from {@link #write} and aborted the whole export, EPUB dropped the character silently — and both formats now
     * fail identically, at the one segment responsible.
     */
    private Result<String> restoreTree(RestoredContent restored) {
        if (XmlCharacters.hasUnwritableCharacter(restored.text())) {
            return Result.err(unwritableCharacterError());
        }
        return Result.ok(restored.text());
    }

    private AppError unwritableCharacterError() {
        log.warn("Refused a restored segment carrying a character that no XML document can represent");
        return AppError.of(
                ErrorCode.validation,
                "This translation could not be restored",
                "The translated text contains a character that cannot be stored in a book of this format — a"
                        + " control character or an incomplete symbol. Nothing was restored, and the rest of the"
                        + " book is unaffected.",
                SafeDetails.empty().render(),
                null);
    }

    /**
     * The Markdown-only tail of restore: escape any construct the model's text introduced that the source did not
     * contain, then verify the escaped text still parses to the same construct multiset as the segment's own
     * source text — the requirements <em>Escape model-introduced Markdown punctuation when restoring</em> and
     * <em>Verify that a restored Markdown segment keeps its structure</em>. Reached only from {@link #unmask};
     * {@link #write} never calls {@link #unmask} — reassembly writes target text straight into the skeleton — so
     * the zero-edit write path never reaches this guard, on any format, and must not be made to.
     */
    private Result<String> restoreMarkdown(Segment segment, RestoredContent restored) {
        final String escaped = MarkdownEscaper.escapeModelIntroduced(
                restored.text(), restored.fragmentRanges(), segment.sourceInner(), segment.kind());
        if (!MarkdownStructureCheck.matches(segment.sourceInner(), escaped, segment.kind())) {
            return Result.err(structureError());
        }
        return Result.ok(escaped);
    }

    private AppError structureError() {
        log.warn("Refused a restored Markdown segment whose structure no longer matches its source");
        return AppError.of(
                ErrorCode.validation,
                "This translation could not be restored",
                "Restoring this translated passage would change the book's Markdown formatting — a heading, list,"
                        + " emphasis or other structure the original did not have, or the loss of one it did.",
                SafeDetails.empty().render(),
                null);
    }

    /**
     * Builds the rendered details once and logs <em>that</em>, rather than the raw token lists. The observed list
     * is scanned out of a provider response, so its size and its tokens' lengths are the model's choice, not the
     * system's; {@link SafeDetails#withPlaceholderMultiset} is where that side is bounded, and logging its output
     * is what keeps the same bound on the log line.
     */
    private AppError gateError(GateOutcome outcome) {
        final String details = SafeDetails.empty()
                .withPlaceholderMultiset(outcome.expected(), outcome.observed())
                .render();
        log.warn("Refused a translated segment whose placeholder multiset does not match its masked form: {}", details);
        return AppError.of(
                ErrorCode.validation,
                "This translation could not be restored",
                "The translated text's formatting placeholders do not match the original segment's — one or more"
                        + " were dropped, duplicated, or invented. Nothing was restored.",
                details,
                null);
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
