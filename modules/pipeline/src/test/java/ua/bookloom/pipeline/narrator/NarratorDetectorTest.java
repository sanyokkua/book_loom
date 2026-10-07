package ua.bookloom.pipeline.narrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorHint.Person;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;

/** Who narrates, read from the source text outside quotes and dialogue. */
class NarratorDetectorTest {

    private static final List<String> FIRST_PERSON = List.of(
            "I walked home through the cold rain. I did not look back.",
            "The street was empty, and I counted the lamps as I passed them.",
            "I had left the letter on the table. I knew that I would regret it.",
            "When I reached the gate, I stopped and listened for a long time.");
    private static final List<String> THIRD_PERSON = List.of(
            "He walked home through the cold rain. He did not look back.",
            "The street was empty, and the boy counted the lamps as he passed them.",
            "The letter lay on the table. Nobody knew that he would regret it.",
            "When he reached the gate, he stopped and listened for a long time.");
    private static final List<String> THIRD_PERSON_SPEAKING_I = List.of(
            "\"I will not go,\" said the boy. \"I told you so.\"",
            "“I saw it myself,” the old man answered. “I was there.”",
            "He shrugged and looked at the fire. The room was quiet for a long while.",
            "She said, \"I am tired, and I want to sleep.\" Then she left the room.",
            "The door closed behind them. Nobody spoke again until morning.");

    @TempDir
    private Path tempDir;

    private static Optional<NarratorHint> detect(final List<List<String>> chapters) {
        return NarratorDetector.detect(chapters, "en");
    }

    @Test
    void detect_everyChapterToldAsI_isFirstPersonWithEveryChapterListed() {
        final NarratorHint hint = detect(List.of(FIRST_PERSON, FIRST_PERSON)).orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.FIRST);
        assertThat(hint.chapters()).containsExactly(1, 2);
        assertThat(hint.firstPersonShare()).isGreaterThan(0.3);
    }

    @Test
    void detect_noChapterTold_asI_isThirdPersonWithNoChapters() {
        final NarratorHint hint = detect(List.of(THIRD_PERSON, THIRD_PERSON)).orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.THIRD);
        assertThat(hint.chapters()).isEmpty();
        assertThat(hint.firstPersonShare()).isZero();
    }

    @Test
    void detect_thirdPersonWhoseCharactersSayIInQuotes_isThirdPerson() {
        final NarratorHint hint = detect(List.of(THIRD_PERSON_SPEAKING_I)).orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.THIRD);
    }

    @Test
    void detect_thirdPersonWithBritishSingleQuoteDialogue_isThirdPerson() {
        final List<String> britishDialogue = List.of(
                "‘I will not go,’ said the boy. ‘I told you so, and I mean it.’",
                "‘I saw it myself,’ the old man answered. ‘I was there when it fell.’",
                "He shrugged and looked at the fire. The room was quiet for a long while.",
                "She said, ‘I am tired, and I don’t want to talk.’ Then she left the room.",
                "‘I know,’ he said. ‘I know I should have told you.’",
                "The door closed behind them. Nobody spoke again until morning.");

        assertThat(detect(List.of(britishDialogue)).orElseThrow().person()).isEqualTo(Person.THIRD);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Henry I ruled the land well.",
                "King Henry I ruled the land well.",
                "Part I begins in the north.",
                "Chapter I was short and grey."
            })
    void detect_romanNumeralI_isNotAFirstPersonPronoun(final String sentence) {
        final List<String> chapter = List.of(
                sentence,
                "The old road ran along the river.",
                "The town lay quiet under the snow.",
                "The market opened at dawn each day.",
                "The bells rang across the hills.",
                "The fields were white until spring.");

        assertThat(detect(List.of(chapter)).orElseThrow().person()).isEqualTo(Person.THIRD);
    }

    @Test
    void detect_alternatingChapters_isMixedNamingTheFirstPersonOnes() {
        final NarratorHint hint = detect(List.of(THIRD_PERSON, FIRST_PERSON, THIRD_PERSON, FIRST_PERSON))
                .orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.MIXED);
        assertThat(hint.chapters()).containsExactly(2, 4);
    }

    @Test
    void detect_contractionsOfI_countAsFirstPerson() {
        final List<String> text = List.of(
                "I'm tired. I've walked all day. I'll sleep soon. I'd rather not talk.",
                "The road was long. I'm sure of it. I'm not going back.");

        assertThat(detect(List.of(text)).orElseThrow().person()).isEqualTo(Person.FIRST);
    }

    @Test
    void detect_aChapterTooShortToJudge_isLeftOutOfTheAnswer() {
        final NarratorHint hint =
                detect(List.of(THIRD_PERSON, List.of("I ran."))).orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.THIRD);
    }

    @Test
    void detect_nothingToJudge_hasNoAnswer() {
        assertThat(detect(List.of(List.of("I ran. I hid.")))).isEmpty();
        assertThat(detect(List.of())).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"es", "xx", "pl"})
    void detect_languageWithoutPronounData_hasNoAnswer(final String language) {
        assertThat(NarratorDetector.detect(List.of(FIRST_PERSON), language)).isEmpty();
    }

    @Test
    void detect_ukrainianSourceWithDashDialogue_readsOnlyTheNarration() {
        final List<String> first = List.of(
                "Я повернувся додому пізно ввечері. Я не озирався.",
                "Вулиця була порожня, і я рахував ліхтарі, коли проходив повз них.",
                "Я залишив листа на столі. Я знав, що пошкодую про це.",
                "— Я не піду, — сказав хлопець. — Я вам казав.",
                "Коли я дійшов до брами, я зупинився й довго слухав.");
        final List<String> third = List.of(
                "Він повернувся додому пізно ввечері. Він не озирався.",
                "Вулиця була порожня, і хлопець рахував ліхтарі, коли проходив повз них.",
                "Лист лежав на столі. Ніхто не знав, що він пошкодує.",
                "— Я не піду, — сказав хлопець. — Я вам казав.",
                "Коли він дійшов до брами, він зупинився й довго слухав.");

        assertThat(NarratorDetector.detect(List.of(first), "uk").orElseThrow().person())
                .isEqualTo(Person.FIRST);
        assertThat(NarratorDetector.detect(List.of(third), "uk").orElseThrow().person())
                .isEqualTo(Person.THIRD);
    }

    @Test
    void detect_sequenceFixture_namesChaptersTwoFiveAndSevenAsFirstPerson() throws IOException {
        final Path book = TestBooks.markdown(tempDir.resolve("sequence.md"), fixtureText(), "en");
        final Document document = TestDocuments.documents().open(book).data();

        final NarratorHint hint =
                NarratorDetector.detect(Objects.requireNonNull(document), "en").orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.MIXED);
        assertThat(hint.chapters()).containsExactly(2, 5, 7);
    }

    @Test
    void detect_firstPersonMarkdownBook_isFirstPerson() throws IOException {
        final String text =
                "# One\n\n" + String.join("\n\n", FIRST_PERSON) + "\n\n# Two\n\n" + String.join("\n\n", FIRST_PERSON);
        final Document document = TestDocuments.documents()
                .open(TestBooks.markdown(Files.createFile(tempDir.resolve("first.md")), text))
                .data();

        final NarratorHint hint =
                NarratorDetector.detect(Objects.requireNonNull(document), "en").orElseThrow();

        assertThat(hint.person()).isEqualTo(Person.FIRST);
    }

    private static String fixtureText() throws IOException {
        try (InputStream in = NarratorDetectorTest.class.getResourceAsStream("/eval/sequence/book.md")) {
            return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
