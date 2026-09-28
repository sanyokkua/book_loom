package ua.bookloom.document.txt;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.inspect.BodySegments;
import ua.bookloom.document.inspect.FormatInspection;
import ua.bookloom.document.inspect.LanguageEvidenceReader;
import ua.bookloom.document.inspect.WordCounter;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * Plain text's own {@link FormatInspection} (task 4.2): always readable, and always declaring no language — TXT
 * carries no metadata a book's own language could be read from.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class TxtInspection implements FormatInspection {

    private static final String TXT_TYPE = "TXT";

    private final OpenTxtRegistry registry;

    @Override
    public BookInspection inspect(Path source) {
        Objects.requireNonNull(source, "source");
        log.debug("Inspecting TXT source={}", source);
        return new BookInspection(
                InspectionVerdict.READABLE,
                BookFormat.TXT,
                null,
                TXT_TYPE,
                null,
                LanguageEvidenceReader.evaluate(null, List.of()));
    }

    /** Plain text carries no metadata a cover image could be read from (task 4.3): always none. */
    @Override
    public Optional<CoverImage> cover(Document document) {
        Objects.requireNonNull(document, "document");
        log.debug("TXT declares no cover rule documentId={}", document.id());
        return Optional.empty();
    }

    /** Plain text is one unit (task 4.4): a single node carrying that unit's whole segment count. */
    @Override
    public List<StructureNode> structure(Document document) {
        Objects.requireNonNull(document, "document");
        final Unit unit = document.units().get(0);
        log.debug(
                "TXT structure documentId={} segmentCount={}",
                document.id(),
                unit.segments().size());
        return List.of(new StructureNode("", unit.id(), unit.segments().size(), List.of()));
    }

    /**
     * Plain text carries no images, fonts, code blocks, verse, footnotes or tables, and no protected inline
     * markup — its only statistic is its own segment and word count (task 4.5).
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public BookStats stats(Document document) {
        Objects.requireNonNull(document, "document");
        requireOpen(document);
        final List<Segment> bodySegments = BodySegments.of(document);
        final BookStats stats = new BookStats(
                bodySegments.size(),
                WordCounter.count(bodySegments, document.declaredLang()),
                0,
                0,
                0,
                0,
                0,
                0,
                Set.of());
        log.debug("TXT stats documentId={} segments={} words={}", document.id(), stats.segments(), stats.words());
        return stats;
    }

    /**
     * Plain text carries no resource identifiers (task 4.5): always empty.
     *
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    @Override
    public Set<String> resourceIds(Document document) {
        Objects.requireNonNull(document, "document");
        requireOpen(document);
        log.debug("TXT declares no resource ids documentId={}", document.id());
        return Set.of();
    }

    private void requireOpen(Document document) {
        if (registry.find(document.id()).isEmpty()) {
            throw new DocumentNotOpenException(document.id());
        }
    }
}
