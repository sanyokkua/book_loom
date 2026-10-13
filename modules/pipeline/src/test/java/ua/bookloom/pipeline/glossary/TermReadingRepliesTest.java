package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.TermReading.Kind;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Usage;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.index.WindowPlan;

/**
 * Reading a term-reading reply (15i.N4): what each window's answer says (usage, kind, the pronoun quoted) and the
 * term's two flags, and when a window's gender vote stands — its quote is in that window, outside speech, and, when
 * the code knows the book's language, holds a pronoun of the claimed gender; under Auto the code cannot read pronouns,
 * so the quote standing in the window's narration is all it checks.
 */
class TermReadingRepliesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AUTO = "AUTO";

    /** One window cut by hand around the first place the term stands in the text. */
    private static Window window(final String term, final String text) {
        final int start = text.indexOf(term);
        return new Window(
                new Mention(term, 0, start, start + term.length(), 0, true), 0, 0, text, start, start + term.length());
    }

    private static String windowJson(
            final int number, final String quote, final String usage, final String kind, final String gender) {
        return "{\"window\":" + number + ",\"quote\":\"" + quote.replace("\"", "\\\"") + "\",\"usage\":\"" + usage
                + "\",\"kind\":\"" + kind + "\",\"gender\":\"" + gender + "\"}";
    }

    private static String reply(final boolean speaking, final boolean brand, final String... windows) {
        return "{\"windows\":[" + String.join(",", windows) + "],\"speaking\":" + speaking + ",\"brand\":" + brand
                + "}";
    }

    private static Reading read(
            final String reply, final String term, final List<Window> windows, @Nullable final String language) {
        return TermReadingReplies.read(MAPPER, reply, term, windows, language);
    }

    // The vote filter, one window at a time: the window's text, the quote the model gave, the gender it claimed, the
    // book's analysis language (AUTO: none, so the code cannot read pronouns and checks only that the quote stands in
    // the window's narration) and the vote that stands. "the is no he": the "he" inside "the" is no pronoun; "marks":
    // the [[term]] marks the prompt shows, copied into the quote, read as the term itself.
    @ParameterizedTest(name = "[{index}] {5}")
    @CsvSource(delimiter = '|', nullValues = AUTO, textBlock = """
                Ilsa looked up. She nodded slowly.          | She nodded      | female | en   | FEMALE  | narration
                Ilsa looked up. She nodded slowly.          | She waved       | female | en   | UNKNOWN | not in window
                Ilsa looked up. “She is late,” said Tovrin. | She is late     | female | en   | UNKNOWN | in speech
                Ilsa looked up, and he smiled at her.       | he smiled       | female | en   | UNKNOWN | other gender
                Ilsa looked up, and he smiled at her.       | smiled at her   | female | en   | FEMALE  | object form
                Ilsa opened the door.                       | opened the door | male   | en   | UNKNOWN | the is no he
                Ilsa looked up, and he smiled.              | he smiled       | female | AUTO | FEMALE  | Auto: exists
                Ilsa looked up. “She is late,” said Tovrin. | She is late     | female | AUTO | UNKNOWN | Auto: speech
                Ilsa looked up. She nodded.                 | none            | none   | en   | UNKNOWN | abstains
                Ilsa looked up. She nodded.                 | none            | female | en   | UNKNOWN | no quote
                Ilsa looked up. She nodded.                 |                 | female | en   | UNKNOWN | empty quote
                Ilsa looked up. She nodded.                 | She nodded      | none   | en   | UNKNOWN | no gender
                Ilsa looked up. She nodded.                 | [[Ilsa]] looked up. She nodded | female | en | FEMALE | marks
                Ілса підвела очі. Вона кивнула.             | Вона кивнула    | female | uk   | FEMALE  | uk pronoun
                Ілса підвела очі. Він кивнув.               | Він кивнув      | female | uk   | UNKNOWN | uk other
                Ilsa looked up. She nodded.                 | he              | male   | en   | UNKNOWN | he of She
                Ilsa looked up, and he smiled.              | he smiled       | female | ja   | FEMALE  | no table: exists
                Ilsa looked up. “She is late,” said Tovrin. | She is late     | female | ja   | UNKNOWN | no table: speech
                """)
    void read_oneWindowsVote_standsOnlyWhenItsQuoteBearsItOut(
            final String text,
            @Nullable final String quote,
            final String gender,
            @Nullable final String language,
            final Gender expected,
            final String why) {
        final Reading reading = read(
                reply(false, false, windowJson(1, java.util.Objects.toString(quote, ""), "name", "person", gender)),
                text.substring(0, 4),
                List.of(window(text.substring(0, 4), text)),
                language);

        assertThat(reading.windows()).extracting(WindowReading::vote).as(why).containsExactly(expected);
    }

    // IF a dash-led line of the next paragraph were read as narration, THEN the speaker's "she" in a Ukrainian dialogue
    // would vote for the name the window is about; the window comes from the book's index, which joins paragraphs.
    @Test
    void read_quoteInADashLedLineOfTheNextParagraph_dropsTheVote() {
        final BookIndex index = GlossaryTestSegments.of(
                        List.of("Ілса мовчала біля вікна.", "— Вона прийде, — сказав Товрин."))
                .index();
        final List<Window> windows =
                index.windows(index.mentions(List.of("Ілса"), Case.SENSITIVE), new WindowPlan(1, 1, 450));

        final Reading reading = read(
                reply(false, false, windowJson(1, "Вона прийде", "name", "person", "female")), "Ілса", windows, "uk");

        assertThat(windows.getFirst().text()).contains("Вона прийде");
        assertThat(reading.windows()).extracting(WindowReading::vote).containsExactly(Gender.UNKNOWN);
    }

    // The same across paragraphs in English: the quoted line after the name's paragraph is speech.
    @Test
    void read_quoteInsideTheNextParagraphsQuotedSpeech_dropsTheVote() {
        final BookIndex index = GlossaryTestSegments.of(
                        List.of("Ilsa stood by the window.", "“She will come,” said Tovrin."))
                .index();
        final List<Window> windows =
                index.windows(index.mentions(List.of("Ilsa"), Case.SENSITIVE), new WindowPlan(1, 1, 450));

        final Reading reading = read(
                reply(false, false, windowJson(1, "She will come", "name", "person", "female")), "Ilsa", windows, "en");

        assertThat(reading.windows()).extracting(WindowReading::vote).containsExactly(Gender.UNKNOWN);
    }

    // IF a quote from window 2 could vote under window 1, THEN a pronoun about someone else could vote twice.
    @Test
    void read_quoteFromAnotherWindow_dropsTheVote() {
        final List<Window> windows =
                List.of(window("Ilsa", "Ilsa sat down."), window("Ilsa", "Ilsa rose. She left the room."));

        final Reading reading = read(
                reply(
                        false,
                        false,
                        windowJson(1, "She left the room", "name", "person", "female"),
                        windowJson(2, "She left the room", "name", "person", "female")),
                "Ilsa",
                windows,
                "en");

        assertThat(reading.windows())
                .extracting(WindowReading::number, WindowReading::claimed, WindowReading::vote)
                .containsExactly(tuple(1, Gender.FEMALE, Gender.UNKNOWN), tuple(2, Gender.FEMALE, Gender.FEMALE));
    }

    @Test
    void read_usageKindAndFlags_areReadFromTheReply() {
        final List<Window> windows = List.of(
                window("Mark", "Mark poured the beer."),
                window("Mark", "They passed Mark Street."),
                window("Mark", "Mark Industries hired him."),
                window("Mark", "Uncle Mark waved."));

        final Reading reading = read(
                reply(
                        true,
                        true,
                        windowJson(1, "none", "name", "person", "none"),
                        windowJson(2, "none", "part-of-name", "place", "none"),
                        windowJson(3, "none", "part-of-name", "organisation", "none"),
                        windowJson(4, "none", "title", "thing", "none")),
                "Mark",
                windows,
                "en");

        assertThat(reading.term()).isEqualTo("Mark");
        assertThat(reading.speaking()).isTrue();
        assertThat(reading.brand()).isTrue();
        assertThat(reading.windows())
                .extracting(WindowReading::number, WindowReading::usage, WindowReading::kind)
                .containsExactly(
                        tuple(1, Usage.NAME, Kind.PERSON),
                        tuple(2, Usage.PART_OF_NAME, Kind.PLACE),
                        tuple(3, Usage.PART_OF_NAME, Kind.ORGANISATION),
                        tuple(4, Usage.TITLE, Kind.THING));
    }

    // IF the bartender's name and "a mark on the glass" were read alike, THEN the name would be removed: the ordinary
    // use reads as a word.
    @Test
    void read_ordinaryWordUse_readsAsWordAndKeepsTheQuote() {
        final Reading reading = read(
                reply(false, false, windowJson(1, "a mark on the glass", "word", "other", "none")),
                "mark",
                List.of(window("mark", "She left a mark on the glass.")),
                "en");

        assertThat(reading.windows())
                .extracting(WindowReading::usage, WindowReading::kind, WindowReading::quote, WindowReading::vote)
                .containsExactly(tuple(Usage.WORD, Kind.OTHER, "a mark on the glass", Gender.UNKNOWN));
    }

    @Test
    void read_usageOrKindTheSchemaDoesNotList_readsAsUnsureAndOther() {
        final Reading reading = read(
                reply(false, false, windowJson(1, "none", "nickname", "animal", "none")),
                "Mark",
                List.of(window("Mark", "Mark poured the beer.")),
                "en");

        assertThat(reading.windows())
                .extracting(WindowReading::usage, WindowReading::kind)
                .containsExactly(tuple(Usage.UNSURE, Kind.OTHER));
    }

    // IF a window number the call never showed were kept, THEN a vote could rest on text the model never saw.
    @ParameterizedTest
    @CsvSource({"0", "3", "-1"})
    void read_windowNumberTheCallNeverShowed_isDropped(final int number) {
        final List<Window> windows = List.of(window("Ilsa", "Ilsa sat down."), window("Ilsa", "Ilsa rose."));

        final Reading reading =
                read(reply(false, false, windowJson(number, "none", "name", "person", "none")), "Ilsa", windows, "en");

        assertThat(reading.windows()).isEmpty();
    }

    @Test
    void read_windowAnsweredTwice_keepsTheFirstAnswer() {
        final Reading reading = read(
                reply(
                        false,
                        false,
                        windowJson(1, "She nodded", "name", "person", "female"),
                        windowJson(1, "none", "word", "other", "none")),
                "Ilsa",
                List.of(window("Ilsa", "Ilsa looked up. She nodded.")),
                "en");

        assertThat(reading.windows())
                .extracting(WindowReading::usage, WindowReading::vote)
                .containsExactly(tuple(Usage.NAME, Gender.FEMALE));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"not json at all", "{\"verdicts\":[]}", "{\"windows\":\"none\"}", "[]"})
    void read_unreadableReply_isNoReading(final String reply) {
        final Reading reading = read(reply, "Ilsa", List.of(window("Ilsa", "Ilsa sat down.")), "en");

        assertThat(reading).isEqualTo(Reading.none("Ilsa"));
    }

    // A reply a small model wraps in a code fence still reads.
    @Test
    void read_replyInACodeFence_isRead() {
        final String fenced =
                "```json\n" + reply(false, false, windowJson(1, "She nodded", "name", "person", "female")) + "\n```";

        final Reading reading = read(fenced, "Ilsa", List.of(window("Ilsa", "Ilsa looked up. She nodded.")), "en");

        assertThat(reading.windows()).extracting(WindowReading::vote).containsExactly(Gender.FEMALE);
    }

    // Flags a reply leaves out read as false: a speaking name or a brand is never assumed.
    @Test
    void read_flagsMissing_readAsFalse() {
        final Reading reading = read(
                "{\"windows\":[" + windowJson(1, "none", "name", "person", "none") + "]}",
                "Ilsa",
                List.of(window("Ilsa", "Ilsa sat down.")),
                "en");

        assertThat(reading.speaking()).isFalse();
        assertThat(reading.brand()).isFalse();
        assertThat(reading.windows()).hasSize(1);
    }
}
