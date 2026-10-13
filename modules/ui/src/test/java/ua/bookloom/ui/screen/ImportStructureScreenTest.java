package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The reading-order tree and the segment browser on the import screen, reused from the structure screen with the same
 * ids: picking a part in the tree lists its segments, picking a segment shows its text below the list.
 */
class ImportStructureScreenTest extends ImportBookTestBase {

    // IF the tree were missing or out of order on Import, THEN the book's structure could not be checked there.
    @Test
    void tree_bookOpened_rendersEachChapterInOrderWithItsSegmentCount() throws TimeoutException {
        openFactsBook();

        assertThat(renderedRows()).containsExactly(List.of("Chapter 1", "4"), List.of("Chapter 2", "1"));
    }

    // IF nothing were picked at the start, THEN the segments card would open empty beside a full tree.
    @Test
    void segments_bookOpened_firstChapterIsListedWithoutAPick() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);

        assertThat(segmentRows())
                .containsExactly(
                        List.of("ch1 · p01", "Heading", "Chapter One", "1/2"),
                        List.of("ch1 · p02", "Paragraph", LONG_TEXT, "1/2"),
                        List.of("ch1 · p03", "Paragraph", "Second paragraph.", "2/2"),
                        List.of("ch1 · p04", "Paragraph", "Third paragraph.", "2/2"));
        assertThat(labelText("structure-segments-header")).isEqualTo("4 segments · 2 chunks (planned)");
    }

    // IF picking another part kept the old rows, THEN the list would not follow the tree.
    @Test
    void segments_anotherNodePicked_listsThatNodesSegments() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);

        onFx(() -> tree().getSelectionModel().select(1));
        awaitFx(() -> segmentRows().size() == 1);

        assertThat(segmentRows()).containsExactly(List.of("ch2 · p01", "Paragraph", "The second chapter.", "1/1"));
        assertThat(projects.segmentCalls()).containsExactly("p1/unit-0", "p1/unit-1");
    }

    // IF the picked segment's text were not shown whole below the list, THEN a person could not read what the model is
    // given.
    @Test
    void preview_segmentPicked_showsItsWholeTextBelowTheList() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);

        pickSegment(1);

        assertThat(previewText()).isEqualTo(LONG_TEXT);
        assertThat(labelText("structure-preview-meta")).isEqualTo("ch1 · p02 · Paragraph · 5 tokens · chunk 1 of 2");
        assertThat(boundsOf("structure-preview").getMinY())
                .isGreaterThanOrEqualTo(boundsOf("structure-segments").getMaxY());
    }

    // IF the search narrowed nothing on Import, THEN the browser would be reused in name only.
    @Test
    void segments_searchText_narrowsTheListToTheMatchingSegments() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);

        filterSegments("third");
        awaitFx(() -> segmentRows().size() == 1);

        assertThat(segmentRows()).containsExactly(List.of("ch1 · p04", "Paragraph", "Third paragraph.", "2/2"));
    }

    // IF the old Structure screen's own footer were copied onto Import, THEN there would be two ways to go on.
    @Test
    void footer_bookOpened_holdsOnlyImportsCancelAndContinue() throws TimeoutException {
        openFactsBook();

        assertThat(isShown("import-cancel")).isTrue();
        assertThat(isShown("import-continue")).isTrue();
        assertThat(optional("structure-continue")).isNull();
        assertThat(optional("structure-back")).isNull();
    }

    // IF the Structure step stopped working before it is removed (15i.U3), THEN a person could reach a broken screen.
    @Test
    void structureStep_bookOpened_stillShowsItsOwnScreen() throws TimeoutException {
        openFactsBook();

        onFx(() -> shell.activate(ViewNames.STRUCTURE));

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.STRUCTURE);
        assertThat(isShown("structure-tree")).isTrue();
        assertThat(isShown("structure-continue")).isTrue();
    }

    // IF opening the book left the tree and the checks asking for the same project twice, THEN the work would be done
    // twice per book.
    @Test
    void segments_bookOpened_theFirstPartIsAskedForOnce() throws TimeoutException {
        openFactsBook();
        awaitFx(() -> segmentRows().size() == 4);

        assertThat(projects.segmentCalls()).containsExactly("p1/unit-0");
    }
}
