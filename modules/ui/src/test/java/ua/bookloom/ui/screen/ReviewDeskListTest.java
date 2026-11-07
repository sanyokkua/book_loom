package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.ReviewViewModel;

/** The desk's list: the five filters and their counts, the markers, the lines and the second-look explanation. */
class ReviewDeskListTest extends ReviewDeskTestBase {

    private void openWithFourAndThree() throws Exception {
        desk.willAnswerQueue(ReviewFilter.ALL_SEGMENTS, ReviewDeskFixtures.keptAsSource(7));
        openDeskWith(ReviewDeskFixtures.fourToReview(), ReviewDeskFixtures.worthASecondLook());
    }

    // Four to review, three worth a second look, two changed by proofread and one waiting on a gender.
    private void openWithFourThreeTwoOne() throws Exception {
        desk.willAnswerQueue(ReviewFilter.ALL_SEGMENTS, ReviewDeskFixtures.keptAsSource(7));
        desk.willAnswerQueue(
                ReviewFilter.CHANGED_BY_PROOFREAD,
                List.of(ReviewFixtures.keptRewrite(), ReviewFixtures.withUserEdit()));
        desk.willAnswerQueue(ReviewFilter.WAITING_ON_GENDER, List.of(ReviewFixtures.accepted()));
        desk.willAnswerSegment(ReviewFixtures.keptRewrite());
        openDeskWith(ReviewDeskFixtures.fourToReview(), ReviewDeskFixtures.worthASecondLook());
        // The desk reads its counts again when it is shown afresh, as a person reopening it would see them.
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 4, 0, 0, 0, 0, 0, 3, 0, 2, 1));
        onFx(() -> injector.getInstance(ReviewViewModel.class).refreshCount());
    }

    // IF the filters did not show their counts, THEN the person could not tell how much is waiting in each.
    @ParameterizedTest
    @CsvSource({
        "ALL_FLAGGED,To review (4)",
        "SUSPICIOUS,Worth a second look (3)",
        "CHANGED_BY_PROOFREAD,Changed by proofread (2)",
        "WAITING_ON_GENDER,Waiting on a gender (1)",
        "ALL_SEGMENTS,All segments"
    })
    void filter_eachOfFive_readsItsWordAndCount(final ReviewFilter filter, final String words) throws Exception {
        openWithFourThreeTwoOne();

        chooseFilter(filter);

        awaitFx(() -> textOf("review-filter").contains(words));
        assertThat(fx(() -> filterBox().getItems()))
                .containsExactly(
                        ReviewFilter.ALL_FLAGGED,
                        ReviewFilter.SUSPICIOUS,
                        ReviewFilter.CHANGED_BY_PROOFREAD,
                        ReviewFilter.WAITING_ON_GENDER,
                        ReviewFilter.ALL_SEGMENTS);
    }

    // IF a kept rewrite showed no verdict, THEN the person could not see what the last pass changed or why.
    @Test
    void changedByProofread_keptRewrite_showsTheVerdictAndTheDiff() throws Exception {
        openWithFourThreeTwoOne();

        chooseFilter(ReviewFilter.CHANGED_BY_PROOFREAD);
        awaitFx(() -> "ch4 · p07".equals(selectedLocator()));

        awaitFx(() -> optional("review-proofread-verdict") != null);
        assertThat(labelText("review-proofread-verdict")).isEqualTo("Proofread · kept: gender of Ліна is female");
        assertThat(labelText("review-proofread-removed")).isEqualTo("− Ліна сказав.");
        assertThat(labelText("review-proofread-added")).isEqualTo("+ Ліна сказала.");
    }

    // IF All segments were offered only after an Unattended run, THEN a person who chose Assisted could not browse.
    @Test
    void filter_runWasNotUnattended_stillOffersAllSegments() throws Exception {
        launchWith(ua.bookloom.api.pipeline.ReviewMode.ASSISTED);
        openWithFourAndThree();

        assertThat(fx(() -> filterBox().getItems())).contains(ReviewFilter.ALL_SEGMENTS);
    }

    // IF the chip and the row said the rule's code ("wrong lang?", "omission"), THEN the person would read the engine's
    // words where the Words table says what may be wrong (W6-4, M11).
    @Test
    void chipAndRow_wrongLanguageFinding_sayWhatMayBeWrongInPlainWords() throws Exception {
        openDeskWith(List.of(ReviewFixtures.wrongLanguage()), List.of());

        assertThat(labelText("review-worst")).isEqualTo("part of it may be in another language");
        assertThat(fx(((javafx.scene.control.Label) rowList().lookup(".review-row-line"))::getText))
                .startsWith("part of it may be in another language");
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
                .containsExactly("a name may not match the glossary · Гейл відчинив старі двері.");
    }

    // IF "worth a second look" were a bare label, THEN a person could not tell what the audit doubts or why; it
    // explains itself on hover in the language of the window, on the list and on the closed box.
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            en | Accepted segments that the audit or the Proofread stage still doubts
            uk | Прийняті сегменти, які аудит або вичитка ще ставлять під сумнів
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
