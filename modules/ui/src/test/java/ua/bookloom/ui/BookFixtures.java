package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NarratorHint;

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
    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

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

    /**
     * What importing a readable book answers when a test states the whole structure tree and the statistics.
     *
     * @param projectId the id of the project the import created
     * @param format the format the inspection reports
     * @param structure the top-level structure nodes, each with its own children
     * @param stats the statistics the profile carries
     * @return the answer
     */
    public static ImportedBook structured(
            final String projectId,
            final BookFormat format,
            final List<StructureNode> structure,
            final BookStats stats) {
        final ImportedBook base = imported(projectId, format, "en", "Title", "Author");
        final BookProfile profile = new BookProfile("Title", "Author", null, structure, stats, Set.of());
        return new ImportedBook(base.projectId(), base.inspection(), profile, base.brief());
    }

    /** A structure node with a unit and a count and no children. */
    public static StructureNode leaf(final String title, final int segments) {
        return new StructureNode(title, "unit-" + title, segments, List.of());
    }

    /**
     * A book whose brief has no source language, as the project service leaves it when the book declares none the
     * application recognises.
     *
     * @param projectId the project's id
     * @param format the book's format
     * @param segmentsPerNode the segment count of each top-level node
     * @return the answer, whose brief holds neither language
     */
    public static ImportedBook declaringNoLanguage(
            final String projectId, final BookFormat format, final int... segmentsPerNode) {
        final ImportedBook book = imported(projectId, format, null, null, null, segmentsPerNode);
        return new ImportedBook(
                book.projectId(),
                book.inspection(),
                book.profile(),
                Objects.requireNonNull(book.brief()).withLanguages(null, null));
    }

    /**
     * The same book with another source language preselected in its brief, as the project service does from the
     * language evidence.
     *
     * @param book the imported book
     * @param sourceLanguage the preselected source language tag
     * @return the book with that source in its brief
     */
    public static ImportedBook withSource(final ImportedBook book, final String sourceLanguage) {
        return new ImportedBook(
                book.projectId(),
                book.inspection(),
                book.profile(),
                Objects.requireNonNull(book.brief()).withLanguages(sourceLanguage, null));
    }

    /**
     * The same book with what the project service found about its narrator.
     *
     * @param book the imported book
     * @param hint the narrator hint the import carries
     * @return the book with that hint
     */
    public static ImportedBook withNarratorHint(final ImportedBook book, final NarratorHint hint) {
        return new ImportedBook(book.projectId(), book.inspection(), book.profile(), book.brief(), hint);
    }

    /** A hint that says a book is told in the first person throughout. */
    public static NarratorHint firstPersonHint() {
        return new NarratorHint(NarratorHint.Person.FIRST, 0.31, List.of(1, 2, 3));
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

    /** A one-pixel PNG, the smallest picture the image decoder accepts, for a book that has a cover. */
    public static CoverImage pngCover() {
        return new CoverImage("cover.png", "image/png", Base64.getDecoder().decode(ONE_PIXEL_PNG));
    }

    /**
     * What importing a readable book answers when a test states the whole inspection: one structure node per chapter
     * and the statistics the card reports.
     *
     * @param projectId the id of the project the import created
     * @param format the format the inspection reports
     * @param formatVersion the version the inspection reports, or {@code null}
     * @param title the profile's title, or {@code null}
     * @param author the profile's author, or {@code null}
     * @param evidence what the metadata says about the language
     * @param chapters how many top-level structure nodes the profile has
     * @param words the statistics' word count
     * @param images the statistics' image count
     * @param fonts the statistics' font count
     * @param cover the cover, or {@code null}
     * @return the answer
     */
    public static ImportedBook inspected(
            final String projectId,
            final BookFormat format,
            final @Nullable String formatVersion,
            final @Nullable String title,
            final @Nullable String author,
            final LanguageEvidence evidence,
            final int chapters,
            final int words,
            final int images,
            final int fonts,
            final @Nullable CoverImage cover) {
        final List<StructureNode> structure = new ArrayList<>();
        for (int n = 0; n < chapters; n++) {
            structure.add(new StructureNode("Chapter " + (n + 1), "unit-" + n, 1, List.of()));
        }
        final BookProfile profile = new BookProfile(
                title,
                author,
                cover,
                structure,
                new BookStats(chapters, words, images, 0, fonts, 0, 0, 0, Set.of()),
                Set.of());
        final BookInspection inspection =
                new BookInspection(InspectionVerdict.READABLE, format, formatVersion, format.name(), null, evidence);
        final String declared = evidence.declared();
        return new ImportedBook(
                projectId, inspection, profile, BookBrief.defaults(declared == null ? DEFAULT_LANGUAGE : declared));
    }

    /** The specification's first scenario: EPUB 2.0 in English, 11 chapters, 78,214 words, 7 images, no fonts, a cover. */
    public static ImportedBook frankensteinInspected() {
        return inspected(
                "p1",
                BookFormat.EPUB,
                "EPUB 2.0",
                "Frankenstein",
                "Mary Shelley",
                new LanguageEvidence("en", "en", "en", LanguageEvidence.Verdict.MATCH),
                11,
                78_214,
                7,
                0,
                pngCover());
    }

    /** Language evidence for a book whose metadata says {@code raw}, normalized to {@code declared}, and whose text is {@code content}. */
    public static LanguageEvidence evidence(
            final @Nullable String raw,
            final @Nullable String declared,
            final @Nullable String content,
            final LanguageEvidence.Verdict verdict) {
        return new LanguageEvidence(raw, declared, content, verdict);
    }

    /**
     * What importing an encrypted book answers: no project, and the DRM verdict with the scheme when one is named.
     *
     * @param detectedType the type the inspection recognised
     * @param scheme the encryption scheme's name, or {@code null} when unidentified
     * @return the answer
     */
    public static ImportedBook drmProtected(final String detectedType, final @Nullable String scheme) {
        final BookInspection inspection = new BookInspection(
                InspectionVerdict.DRM_PROTECTED,
                null,
                null,
                detectedType,
                scheme,
                new LanguageEvidence(null, null, null, LanguageEvidence.Verdict.ABSENT));
        return new ImportedBook(null, inspection, null, null);
    }
}
