package ua.bookloom.document.inspect;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * One format's own inspection of a candidate file before it is opened (task 4.2), implemented inside that format's
 * own package so the parser types it needs ({@code OpfParser}, {@code ParsedFb2}, {@code Frontmatter}, and the
 * rest) stay package-private there rather than becoming an accidental public API.
 */
public interface FormatInspection {

    /**
     * Inspects {@code source}, which {@link BookInspectorService} has already matched to this inspection's own
     * format.
     *
     * @param source the candidate file, already confirmed by extension and leading bytes to be this format
     * @return the inspection outcome — {@code READABLE} or {@code DRM_PROTECTED}, this being one format's own
     *     inspection; {@code UNSUPPORTED} is the façade's own answer for every other file
     */
    BookInspection inspect(Path source);

    /**
     * Extracts {@code document}'s cover image (task 4.3), by whichever rule this format defines for finding one.
     * Reports no cover, never a failure, when no rule resolves one — a book's cover is a courtesy for a person
     * recognising it on the import card, not something its absence should ever block on.
     *
     * @param document a document this inspection's format already opened, still open in this session
     * @return the cover image, or empty when this format declares none, or its declaration resolves to nothing
     *     this book's archive actually contains
     */
    Optional<CoverImage> cover(Document document);

    /**
     * Builds {@code document}'s structure tree from its own navigation (task 4.4), counting every body segment in
     * exactly one node so the top-level nodes' counts add up to the book's total.
     *
     * @param document a document this inspection's format already opened, still open in this session
     * @return the top-level structure nodes, in reading order; empty when no open state remains for this document
     */
    List<StructureNode> structure(Document document);

    /**
     * Computes {@code document}'s statistics (task 4.5) — segment, word, image, code-block, font, verse-line,
     * footnote and table counts, plus the kinds of inline formatting protected as placeholders — from this
     * format's own already-parsed state, never by re-reading the source.
     *
     * @param document a document this inspection's format already opened, still open in this session
     * @return the computed statistics
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    BookStats stats(Document document);

    /**
     * Collects every resource identifier {@code document} carries (task 4.5) — image and font ids, plus every id
     * an internal link targets — read from this format's own already-parsed state.
     *
     * @param document a document this inspection's format already opened, still open in this session
     * @return the resource ids; never null, empty when this format declares none
     * @throws DocumentNotOpenException if no open state remains for {@code document}'s id
     */
    Set<String> resourceIds(Document document);
}
