package ua.bookloom.pipeline.export;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.proofread.MarkList;

/**
 * What the export reads from the stores once, before anything is written: the records it writes and the book, map and
 * open deferrals the proofread summary and the banners are counted from.
 *
 * @param book the brief, the opened book, the glossary and the recurring terms as they are now
 * @param records the stored records
 * @param open the open deferrals
 */
@Slf4j
record ProofreadInputs(MarkList.Book book, List<SegmentRecord> records, List<Deferral> open) {

    /** Rejects a missing part and copies the lists. */
    ProofreadInputs {
        Objects.requireNonNull(book, "book");
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        open = List.copyOf(Objects.requireNonNull(open, "open"));
    }

    /**
     * Reads the stores of one project.
     *
     * @param parts the non-null stores
     * @param project the non-null stored project, whose brief names a target language
     * @param opened the non-null opened book
     * @return the inputs, or the first storage error
     */
    static Result<ProofreadInputs> read(final ExportParts parts, final Project project, final Document opened) {
        final String id = project.id();
        log.debug("export step=read-stores project={}", id);
        return parts.segments()
                .all(id)
                .flatMap(records -> parts.deferrals()
                        .open(id)
                        .flatMap(open -> parts.glossary()
                                .all(id)
                                .flatMap(glossary -> parts.lexicon()
                                        .all(id)
                                        .map(lexicon -> new ProofreadInputs(
                                                new MarkList.Book(project.brief(), opened, glossary, lexicon),
                                                records,
                                                open)))));
    }
}
