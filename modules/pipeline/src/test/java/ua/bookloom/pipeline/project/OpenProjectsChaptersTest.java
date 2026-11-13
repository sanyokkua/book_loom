package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.index.IndexTestBooks;

/** The chapters of an open book come from the structure it was put with. */
class OpenProjectsChaptersTest {

    private static final String PROJECT = "p1";
    private static final int CHAPTER_PARAGRAPHS = 12;
    private static final BookBrief BRIEF = BookBrief.defaults("en").withLanguages("en", "uk");
    // 100 characters, 29 tokens: twelve make a chapter over the 300-token minimum.
    private static final String PARAGRAPH =
            "The boats waited by the shore while the river ran slowly past the old mill and the bridge beyond it.";

    private static String[] paragraphs() {
        return Collections.nCopies(CHAPTER_PARAGRAPHS, PARAGRAPH).toArray(String[]::new);
    }

    @Test
    void chapters_noBookOpen_null() {
        assertThat(new OpenProjects().chapters(PROJECT, BRIEF)).isNull();
    }

    @Test
    void chapters_putWithStructure_readFromThatStructure() {
        final Document document = IndexTestBooks.document(
                IndexTestBooks.unit("ch1", UnitRole.BODY, paragraphs()),
                IndexTestBooks.unit("ch1b", UnitRole.BODY, paragraphs()));
        final OpenProjects open = new OpenProjects();
        open.put(
                PROJECT,
                document,
                null,
                List.of(
                        new StructureNode("One", "ch1", CHAPTER_PARAGRAPHS, List.of()),
                        new StructureNode("ch1b.xhtml", "ch1b", CHAPTER_PARAGRAPHS, List.of(), null, false)));

        final ChapterBoundaries chapters = Objects.requireNonNull(open.chapters(PROJECT, BRIEF), "chapters");

        assertThat(chapters.chapters())
                .extracting(ChapterBoundaries.Chapter::key)
                .containsExactly("ch1:0");
    }

    @Test
    void chapters_putWithoutStructure_eachUnitIsListed() {
        final OpenProjects open = new OpenProjects();
        open.put(
                PROJECT,
                IndexTestBooks.document(
                        IndexTestBooks.unit("ch1", UnitRole.BODY, paragraphs()),
                        IndexTestBooks.unit("ch2", UnitRole.BODY, paragraphs())));

        final ChapterBoundaries chapters = Objects.requireNonNull(open.chapters(PROJECT, BRIEF), "chapters");

        assertThat(chapters.chapters())
                .extracting(ChapterBoundaries.Chapter::key)
                .containsExactly("ch1:0", "ch2:0");
    }
}
