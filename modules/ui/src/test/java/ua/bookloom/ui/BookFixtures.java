package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.BookBrief;

/**
 * Hand-built import answers for the screen tests: real {@link ImportedBook} records, never mocks, whose node and
 * segment counts, title, author and declared language are exactly what a test states.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookFixtures {

    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * What importing a readable book answers: a project, and a profile with one structure node per entry of
     * {@code segmentsPerNode}, titled {@code Chapter 1}, {@code Chapter 2} and so on.
     *
     * @param projectId the id of the project the import created
     * @param format the format the inspection reports
     * @param declaredLang the language the book declares, or {@code null} for none
     * @param title the profile's title, or {@code null}
     * @param author the profile's author, or {@code null}
     * @param segmentsPerNode how many segments each top-level node accounts for; its length is the node count
     * @return the answer
     */
    public static ImportedBook imported(
            final String projectId,
            final BookFormat format,
            final @Nullable String declaredLang,
            final @Nullable String title,
            final @Nullable String author,
            final int... segmentsPerNode) {
        final List<StructureNode> structure = new ArrayList<>();
        int segments = 0;
        for (int n = 0; n < segmentsPerNode.length; n++) {
            structure.add(new StructureNode("Chapter " + (n + 1), "unit-" + n, segmentsPerNode[n], List.of()));
            segments += segmentsPerNode[n];
        }
        final BookProfile profile = new BookProfile(
                title, author, null, structure, new BookStats(segments, 0, 0, 0, 0, 0, 0, 0, Set.of()), Set.of());
        final LanguageEvidence evidence = new LanguageEvidence(
                declaredLang,
                declaredLang,
                declaredLang,
                declaredLang == null ? LanguageEvidence.Verdict.ABSENT : LanguageEvidence.Verdict.MATCH);
        final BookInspection inspection =
                new BookInspection(InspectionVerdict.READABLE, format, null, format.name(), null, evidence);
        return new ImportedBook(
                projectId,
                inspection,
                profile,
                BookBrief.defaults(declaredLang == null ? DEFAULT_LANGUAGE : declaredLang));
    }

    /** The book the specification's first scenario opens: project {@code p1}, an EPUB in English, three nodes, nine segments. */
    public static ImportedBook frankensteinImport() {
        return imported("p1", BookFormat.EPUB, "en", "Frankenstein", "Mary Shelley", 2, 3, 4);
    }

    /**
     * What importing a file the application refuses answers: no project, and the inspection's verdict.
     *
     * @param verdict why the file was refused
     * @param detectedType the type the inspection recognised, for example {@code EPUB} or {@code PDF}
     * @return the answer
     */
    public static ImportedBook refused(final InspectionVerdict verdict, final String detectedType) {
        final BookInspection inspection = new BookInspection(
                verdict,
                null,
                null,
                detectedType,
                null,
                new LanguageEvidence(null, null, null, LanguageEvidence.Verdict.ABSENT));
        return new ImportedBook(null, inspection, null, null);
    }
}
