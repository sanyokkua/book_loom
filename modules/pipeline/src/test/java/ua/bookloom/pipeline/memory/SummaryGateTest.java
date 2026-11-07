package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * The vocabulary and source-spelling checks of the chapter-summary gate (design D10) over canned replies: a made-up word
 * is told from a real one by the chapter's decided text, the names and the language's closed word list.
 */
class SummaryGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SummaryGate.Place PLACE = new SummaryGate.Place(1, 1);
    private static final String CHAPTER_SOURCE = "Мирта встретила Глафира у старого моста.";
    private static final List<String> DECIDED = List.of(
            "Мірта вийшла з млина і пішла до міста.", "Біля старого мосту вона зустріла Глафіру і сказала їй правду.");

    private static GlossaryEntry targeted() {
        return GlossaryEntry.of("p:Глафира", "p", "Глафира", "Глафіра", TermType.CHARACTER, Gender.FEMALE, false);
    }

    private static SummaryGate gate(final List<GlossaryEntry> entries, final List<String> decided) {
        return SummaryGate.of(MAPPER, "ru", "uk", map(entries), CHAPTER_SOURCE, decided);
    }

    private static String reply(final String summary) {
        return "{\"summary\":\"" + summary + "\"}";
    }

    @Test
    void read_knownWords_kept() {
        final SummaryGate.Reading reading = gate(List.of(), DECIDED)
                .read(reply("Мірта пішла до міста. Вона сказала правду біля мосту."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).hasSize(2);
        assertThat(reading.unknownWords()).isEmpty();
        assertThat(reading.needsReask()).isFalse();
    }

    @Test
    void read_inflectedNameOfTheGlossary_kept() {
        final SummaryGate.Reading reading = gate(List.of(targeted()), DECIDED)
                .read(reply("Мірта розмовляє з Глафірою біля мосту."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).hasSize(1);
        assertThat(reading.unknownWords()).isEmpty();
    }

    @Test
    void read_inflectedWordOfTheDecidedText_kept() {
        final SummaryGate.Reading reading = gate(List.of(), DECIDED)
                .read(reply("Мірта йде мостом до млинів."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).hasSize(1);
    }

    @Test
    void read_oneUnknownWord_kept() {
        final SummaryGate.Reading reading = gate(List.of(), DECIDED)
                .read(reply("Мірта пішла до шмурляє міста."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).hasSize(1);
        assertThat(reading.unknownWords()).isEmpty();
    }

    @Test
    void read_twoUnknownWords_markedForReaskNamingThem() {
        final SummaryGate.Reading reading = gate(List.of(), DECIDED)
                .read(reply("Мірта пішла до міста. Вона зустріла блазаками шмурляє."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).containsExactly("Мірта пішла до міста.");
        assertThat(reading.unknownWords()).containsExactly("блазаками", "шмурляє");
        assertThat(reading.faulty()).isEqualTo(1);
        assertThat(reading.needsReask()).isTrue();
    }

    @Test
    void read_twoUnknownWordsInTheReask_sentenceDropped() {
        final SummaryGate.Reading reading = gate(List.of(), DECIDED)
                .read(reply("Мірта пішла до міста. Вона зустріла блазаками шмурляє."), PLACE, true)
                .orElseThrow();

        assertThat(reading.clean()).containsExactly("Мірта пішла до міста.");
        assertThat(reading.faulty()).isEqualTo(1);
    }

    @Test
    void read_noDecidedText_vocabularyOff() {
        final SummaryGate.Reading reading = gate(List.of(), List.of())
                .read(reply("Вона зустріла блазаками шмурляє."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).hasSize(1);
        assertThat(reading.unknownWords()).isEmpty();
    }

    @Test
    void read_sourceSpellingOfNameWithTarget_dropped() {
        final SummaryGate.Reading reading = gate(List.of(targeted()), DECIDED)
                .read(reply("Мірта зустріла Глафира біля мосту. Вона пішла до міста."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).containsExactly("Вона пішла до міста.");
        assertThat(reading.dropped()).isEqualTo(1);
        assertThat(reading.needsReask())
                .as("a source spelling is dropped, not asked again")
                .isFalse();
    }

    @Test
    void read_sourceSpellingOfNameWithoutTarget_kept() {
        final GlossaryEntry untargeted =
                GlossaryEntry.of("p:Глафира", "p", "Глафира", null, TermType.CHARACTER, Gender.FEMALE, false);

        final SummaryGate.Reading reading = gate(List.of(untargeted), DECIDED)
                .read(reply("Мірта зустріла Глафира біля мосту."), PLACE, false)
                .orElseThrow();

        assertThat(reading.clean()).hasSize(1);
    }
}
