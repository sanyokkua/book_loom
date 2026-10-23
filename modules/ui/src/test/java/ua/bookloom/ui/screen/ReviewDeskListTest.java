package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.TooltipProbe;

/** The desk's list: the three filters and their counts, the markers, the lines and the second-look explanation. */
class ReviewDeskListTest extends ReviewDeskTestBase {

    private void openWithFourAndThree() throws Exception {
        desk.willAnswerQueue(ReviewFilter.ALL_SEGMENTS, ReviewDeskFixtures.keptAsSource(7));
        openDeskWith(ReviewDeskFixtures.fourToReview(), ReviewDeskFixtures.worthASecondLook());
    }

    // IF the three filters did not show their counts, THEN the person could not tell how much is waiting in each.
    @ParameterizedTest
    @CsvSource({"ALL_FLAGGED,To review (4)", "SUSPICIOUS,Worth a second look (3)", "ALL_SEGMENTS,All segments"})
    void filter_eachOfThree_readsItsWordAndCount(final ReviewFilter filter, final String words) throws Exception {
        openWithFourAndThree();

        chooseFilter(filter);

        assertThat(textOf("review-filter")).contains(words);
        assertThat(fx(() -> filterBox().getItems()))
                .containsExactly(ReviewFilter.ALL_FLAGGED, ReviewFilter.SUSPICIOUS, ReviewFilter.ALL_SEGMENTS);
    }

    // IF All segments were offered only after an Unattended run, THEN a person who chose Assisted could not browse.
    @Test
    void filter_runWasNotUnattended_stillOffersAllSegments() throws Exception {
        launchWith(ua.bookloom.api.pipeline.ReviewMode.ASSISTED);
        openWithFourAndThree();

        assertThat(fx(() -> filterBox().getItems())).contains(ReviewFilter.ALL_SEGMENTS);
    }

    @Test
    void previousAndNext_inToReview_followTheListAndStopAtTheEnds() throws Exception {
        openDeskWith(ReviewDeskFixtures.fourToReview(), List.of());

        onFx(() -> button("review-next").fire());
        awaitFx(() -> "ch3 · p01".equals(selectedLocator()));
        onFx(() -> button("review-prev").fire());
        awaitFx(() -> "ch2 · p05".equals(selectedLocator()));
        onFx(() -> button("review-prev").fire());

        assertThat(selectedLocator()).isEqualTo("ch2 · p05");
    }

    // IF a row did not say what became of its segment, THEN the person could not tell the done from the waiting.
    @Test
    void markers_acceptedYourEditAndLater_eachReadItsWord() throws Exception {
        final SegmentView edited = ReviewFixtures.withUserEdit();
        final SegmentView revised = new SegmentView(
                edited.segmentId(),
                edited.locator(),
                edited.kind(),
                SegmentStatus.REVISED,
                edited.maskedSource(),
                edited.displaySource(),
                edited.maskedMachineTarget(),
                edited.userTarget(),
                edited.maskedUserTarget(),
                edited.findings(),
                edited.judgeScore(),
                edited.path(),
                true,
                null,
                null);
        desk.willAnswerQueue(
                ReviewFilter.ALL_SEGMENTS, List.of(ReviewFixtures.accepted(), revised, ReviewFixtures.lowScore()));
        openDeskWith(ReviewDeskFixtures.fourToReview(), List.of());
        chooseFilter(ReviewFilter.ALL_SEGMENTS);
        awaitFx(() -> rowList().getItems().size() == 3);
        onFx(() -> rowList().getSelectionModel().select(2));
        awaitFx(() -> "ch5 · p12".equals(selectedLocator()));

        chooseMore("review-more-later");

        assertThat(markerWords()).containsExactly("accepted", "your edit", "later");
    }

    @Test
    void row_flaggedNameSegment_readsWhyThenTheTextWithoutTokens() throws Exception {
        openDeskWith(List.of(ReviewFixtures.nameIssue()), List.of());

        assertThat(fx(() -> rowList().lookupAll(".review-row-line").stream()
                        .map(node -> ((javafx.scene.control.Labeled) node).getText())
                        .toList()))
                .containsExactly("name · Гейл відчинив старі двері.");
    }

    // IF "worth a second look" were a bare label, THEN a person could not tell what the audit doubts or why; it
    // explains itself on hover in the language of the window, on the list and on the closed box.
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            en | Accepted segments that a last check of the finished book still doubts
            uk | Прийняті сегменти, які остання перевірка готової книги ще ставить під сумнів
            """)
    void secondLookFilter_selected_explainsItselfOnHover(final String language, final String start) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        openWithFourAndThree();

        chooseFilter(ReviewFilter.SUSPICIOUS);

        assertThat(fx(() -> TooltipProbe.tipText(filterBox().getButtonCell()))).startsWith(start);
    }

    // IF accepting a doubted segment left it listed, THEN "worth a second look" could never get shorter.
    @Test
    void accept_inWorthASecondLook_dropsTheRowAndTheCountByOne() throws Exception {
        openWithFourAndThree();
        chooseFilter(ReviewFilter.SUSPICIOUS);
        awaitFx(() -> "ch2 · p04".equals(selectedLocator()));
        final List<SegmentView> rest = ReviewDeskFixtures.worthASecondLook().subList(1, 3);
        desk.willAnswerQueue(ReviewFilter.SUSPICIOUS, rest);
        desk.willAnswerCounts(ReviewDeskFixtures.counts(4, 2));

        onFx(() -> main().fire());
        awaitFx(() -> rowList().getItems().size() == 2);

        assertThat(rowTexts()).containsExactly("ch2 · p08", "ch4 · p02");
        awaitFx(() -> textOf("review-filter").contains("Worth a second look (2)"));
    }

    // IF the kept-as-source lines showed in the other filters, THEN they would crowd out what needs reading.
    @Test
    void keptAsSource_onlyUnderAllSegments_areMarkedKeptAsSource() throws Exception {
        openWithFourAndThree();

        assertThat(rowTexts()).doesNotContain("nav · 01");
        chooseFilter(ReviewFilter.ALL_SEGMENTS);
        awaitFx(() -> rowList().getItems().size() == 7);

        assertThat(markerWords()).containsOnly("kept as source");
        assertThat(rowTexts()).startsWith("nav · 01", "nav · 02");
    }

    @Test
    void filterBox_isAComboBoxOfTheThreeFilters() throws Exception {
        openWithFourAndThree();

        assertThat(required("review-filter")).isInstanceOf(ComboBox.class);
    }
}
