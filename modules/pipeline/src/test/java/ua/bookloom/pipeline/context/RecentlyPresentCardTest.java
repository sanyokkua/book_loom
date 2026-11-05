package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.IndexTestBooks;

class RecentlyPresentCardTest {

    private static final String HEADING = "[Recently present — context only; do NOT translate]";
    private static final String FILLER = "Туман лежал над рекой, и лодки стояли у берега.";
    private static final BookBrief BRIEF = FrameFixtures.brief("ru", "uk");
    private static final int CHAPTER_PARAGRAPHS = 20;

    private static GlossaryEntry character(final String term, final String target, final Gender gender) {
        return GlossaryEntry.of("p1:" + term, "p1", term, target, TermType.CHARACTER, gender, false);
    }

    // Twenty fillers are 340 tokens, so each chapter is over the 300-token minimum and none joins another.
    private static String[] fillers() {
        return IntStream.range(0, CHAPTER_PARAGRAPHS).mapToObj(index -> FILLER).toArray(String[]::new);
    }

    // Chapters 1 and 3 hold fillers only; chapter 2 has twenty paragraphs, each the filler unless a name is put in it.
    // A chapter 2 that names many falls under the minimum and joins chapter 3, which keeps its number and paragraphs.
    private static Document book(final Map<Integer, String> chapterTwo) {
        final String[] paragraphs = IntStream.range(0, CHAPTER_PARAGRAPHS)
                .mapToObj(index -> chapterTwo.getOrDefault(index, FILLER))
                .toArray(String[]::new);
        return IndexTestBooks.document(
                IndexTestBooks.unit("ch1", UnitRole.BODY, fillers()),
                IndexTestBooks.unit("ch2", UnitRole.BODY, paragraphs),
                IndexTestBooks.unit("ch3", UnitRole.BODY, fillers()));
    }

    // The window starts at the given paragraph of chapter 2 (0-based): two previous pairs, then the focus.
    private static Window windowAt(final Document document, final int start) {
        final List<String> texts =
                document.units().get(1).segments().stream().map(Segment::masked).toList();
        final List<WindowPair> previous = new ArrayList<>();
        previous.add(new WindowPair("ch2:" + start, texts.get(start), null));
        previous.add(new WindowPair("ch2:" + (start + 1), texts.get(start + 1), null));
        final Focus focus = new Focus(List.of(new FocusItem("ch2:" + (start + 2), texts.get(start + 2))), Map.of());
        return new Window(previous, focus, null);
    }

    private static String card(final Document document, final Window window, final List<GlossaryEntry> entries) {
        return card(document, IndexTestBooks.navigation(document), window, entries);
    }

    private static String card(
            final Document document,
            final List<StructureNode> structure,
            final Window window,
            final List<GlossaryEntry> entries) {
        final RunFrame frame = FrameFixtures.frame(document, structure, BRIEF, ModelProfile.defaults());
        return RecentlyPresentCard.of(BookMap.of(entries, List.of(), BRIEF), window, frame);
    }

    @Test
    void card_characterThreeParagraphsBack_listed() {
        // Глафира is in paragraphs 7 to 9 of chapter 2; the window starts at paragraph 13.
        final Document document = book(Map.of(
                6, "Глафира вошла в комнату.",
                7, "Глафира села у окна.",
                8, "Глафира молчала."));

        final String card =
                card(document, windowAt(document, 12), List.of(character("Глафира", "Глафіра", Gender.FEMALE)));

        assertThat(card)
                .isEqualTo(HEADING + "\nScene so far: Chapter 2, after paragraph 12. Recently present, not in this"
                        + " passage: Глафира → Глафіра (female).");
    }

    @Test
    void card_sevenCandidates_fiveMostRecent() {
        // Мирон and Ульяна are only in paragraphs 1 and 2, more than twelve paragraphs before the window at 15.
        final Document document = book(Map.of(
                0, "Мирон ушёл.",
                1, "Ульяна ушла.",
                3, "Тихон пришёл.",
                5, "Аграфена пришла.",
                7, "Савелий пришёл.",
                9, "Прохор пришёл.",
                11, "Евдокия пришла."));
        final List<GlossaryEntry> entries = List.of(
                character("Мирон", "Мирон", Gender.MALE),
                character("Ульяна", "Уляна", Gender.FEMALE),
                character("Тихон", "Тихон", Gender.MALE),
                character("Аграфена", "Аграфена", Gender.FEMALE),
                character("Савелий", "Савелій", Gender.MALE),
                character("Прохор", "Прохор", Gender.MALE),
                character("Евдокия", "Євдокія", Gender.FEMALE));

        final String card = card(document, windowAt(document, 14), entries);

        assertThat(card)
                .endsWith("after paragraph 14. Recently present, not in this passage: Евдокия → Євдокія (female),"
                        + " Прохор → Прохор (male), Савелий → Савелій (male), Аграфена → Аграфена (female),"
                        + " Тихон → Тихон (male).")
                .doesNotContain("Мирон", "Ульяна");
    }

    @Test
    void card_sixInReach_fiveKept() {
        final Document document = book(Map.of(
                2, "Мирон ушёл.",
                3, "Ульяна ушла.",
                5, "Тихон пришёл.",
                7, "Аграфена пришла.",
                9, "Савелий пришёл.",
                11, "Прохор пришёл."));
        final List<GlossaryEntry> entries = List.of(
                character("Мирон", "Мирон", Gender.MALE),
                character("Ульяна", "Уляна", Gender.FEMALE),
                character("Тихон", "Тихон", Gender.MALE),
                character("Аграфена", "Аграфена", Gender.FEMALE),
                character("Савелий", "Савелій", Gender.MALE),
                character("Прохор", "Прохор", Gender.MALE));

        final String card = card(document, windowAt(document, 12), entries);

        assertThat(card)
                .contains("Прохор", "Савелий", "Аграфена", "Тихон", "Ульяна")
                .doesNotContain("Мирон");
    }

    @Test
    void card_chapterStart_empty() {
        final Document document = book(Map.of(5, "Глафира вошла."));
        final Window window = new Window(
                List.of(new WindowPair("ch1:18", FILLER, null), new WindowPair("ch1:19", FILLER, null)),
                new Focus(List.of(new FocusItem("ch2:0", FILLER)), Map.of()),
                FILLER);

        assertThat(card(document, window, List.of(character("Глафира", "Глафіра", Gender.FEMALE))))
                .isEmpty();
    }

    @Test
    void card_nameInWindow_notRepeated() {
        // Глафира is in paragraph 9 and again in the window's first pair; Тихон is in paragraph 10 only.
        final Document document = book(Map.of(
                8, "Глафира вошла.",
                9, "Тихон пришёл.",
                12, "Глафира села у окна."));
        final List<GlossaryEntry> entries =
                List.of(character("Глафира", "Глафіра", Gender.FEMALE), character("Тихон", "Тихон", Gender.MALE));

        final String card = card(document, windowAt(document, 12), entries);

        assertThat(card).contains("not in this passage: Тихон → Тихон (male).").doesNotContain("Глафира");
    }

    @Test
    void card_placeAndLikelyGender_typeAndLikelyWords() {
        final Document document = book(Map.of(8, "Ульяна ушла в Заречье."));
        final List<GlossaryEntry> entries = List.of(
                GlossaryEntry.of("p1:z", "p1", "Заречье", "Заріччя", TermType.PLACE, Gender.UNKNOWN, false),
                GlossaryEntry.of("p1:u", "p1", "Ульяна", "Уляна", TermType.CHARACTER, Gender.UNKNOWN, false)
                        .withSuggestedGender(Gender.FEMALE));

        final String card = card(document, windowAt(document, 12), entries);

        assertThat(card).contains("Заречье → Заріччя (place), Ульяна → Уляна (likely female).");
    }

    @Test
    void card_firstPersonChapterNarratorUnstated_addsNarratorLine() {
        final Map<Integer, String> told = new HashMap<>();
        IntStream.range(0, 12).forEach(index -> told.put(index, "Я шёл к реке. Я видел лодки у берега."));

        final String card = card(book(told), windowAt(book(told), 12), List.of());

        assertThat(card)
                .endsWith("\nThis chapter is told in the first person: keep one gender for the narrator's own"
                        + " \"I\" words throughout, the one the source shows.");
    }

    @Test
    void card_thirdPersonChapter_noNarratorLine() {
        final Document document = book(Map.of());

        assertThat(card(document, windowAt(document, 12), List.of()))
                .isEqualTo(HEADING + "\nScene so far: Chapter 2, after paragraph 12.");
    }

    @Test
    void card_epubContinuationFile_staysInTheChapter() {
        // ch2a is a spine file the navigation does not list, so it continues chapter 2, where Глафира sat at 8.
        final String[] chapterTwo = IntStream.range(0, 10)
                .mapToObj(index -> index == 7 ? "Глафира села у окна." : FILLER)
                .toArray(String[]::new);
        final Document document = IndexTestBooks.document(
                IndexTestBooks.unit("ch1", UnitRole.BODY, fillers()),
                IndexTestBooks.unit("ch2", UnitRole.BODY, chapterTwo),
                IndexTestBooks.unit("ch2a", UnitRole.BODY, Arrays.copyOf(fillers(), 10)));
        final List<StructureNode> structure = List.of(
                new StructureNode("One", "ch1", CHAPTER_PARAGRAPHS, List.of()),
                new StructureNode("Two", "ch2", 10, List.of()),
                new StructureNode("ch2a.xhtml", "ch2a", 10, List.of(), null, false));
        final Window window = new Window(
                List.of(new WindowPair("ch2a:0", FILLER, null), new WindowPair("ch2a:1", FILLER, null)),
                new Focus(List.of(new FocusItem("ch2a:2", FILLER)), Map.of()),
                null);

        final String card = card(document, structure, window, List.of(character("Глафира", "Глафіра", Gender.FEMALE)));

        assertThat(card)
                .isEqualTo(HEADING + "\nScene so far: Chapter 2, after paragraph 10. Recently present, not in this"
                        + " passage: Глафира → Глафіра (female).");
    }
}
