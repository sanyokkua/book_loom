package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;

/**
 * A name whose meaning the book explains or plays on is asked about once (design D5): the reading names the window,
 * only a window the call showed counts, the names pass marks the name as asked, and once the person has answered —
 * closing the question included — no later pass asks again. All names are invented.
 */
class NameReadingsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXPLAINS = "They called the village Brightwater for the bright water of its stream.";
    private static final String PLAIN = "Brightwater locked the gate and went home.";

    private final GlossaryRepository glossary = Guice.createInjector(
                    new DocumentModule(), new InMemoryPersistenceModule())
            .getInstance(GlossaryRepository.class);

    private static Window window(final String text) {
        final int start = text.indexOf("Brightwater");
        final int end = start + "Brightwater".length();
        return new Window(new Mention("Brightwater", 0, start, end, 0, true), 0, 0, text, start, end);
    }

    private static Reading reading(final int explainsName) {
        final String reply = "{\"windows\":[],\"speaking\":true,\"brand\":false,\"explainsName\":" + explainsName + "}";
        return TermReadingReplies.read(MAPPER, reply, "Brightwater", List.of(window(PLAIN), window(EXPLAINS)), "en");
    }

    private GlossaryEntry held(final String target, final TermType type) {
        final GlossaryEntry entry = GlossaryEntry.of(
                        "p1:brightwater", "p1", "Brightwater", null, type, Gender.UNKNOWN, false)
                .withSuggestedTarget(target)
                .withMeaning("bright water");
        return Objects.requireNonNull(glossary.add(entry).data());
    }

    private static int count(final Result<Integer> marked) {
        return Objects.requireNonNull(marked.data(), "marked");
    }

    private GlossaryEntry stored() {
        return Objects.requireNonNull(glossary.all("p1").data()).getFirst();
    }

    @Test
    void reading_explainsName_asksOncePerName() {
        held("Брайтвотер", TermType.PLACE);
        final Map<String, Reading> readings = Map.of("p1:brightwater", reading(2));

        final int first = count(MeaningQuestions.mark(glossary, "p1", readings, NamePolicy.TRANSLITERATE));
        glossary.update(MeaningQuestions.answered(stored(), MeaningChoice.PENDING));
        final int second = count(MeaningQuestions.mark(glossary, "p1", readings, NamePolicy.TRANSLITERATE));

        assertThat(Objects.requireNonNull(readings.get("p1:brightwater")).explanation())
                .isEqualTo(EXPLAINS);
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(stored())
                .extracting(GlossaryEntry::meaningChoice, GlossaryEntry::target, GlossaryEntry::meaning)
                .containsExactly(MeaningChoice.TRANSCRIBE, "Брайтвотер", "bright water");
    }

    // 0 is "no window"; a number the call never showed is no evidence and asks nothing.
    @ParameterizedTest
    @CsvSource({"0", "3", "-1"})
    void reading_explainsNameOutsideTheShownWindows_isNone(final int number) {
        held("Брайтвотер", TermType.PLACE);

        final Reading read = reading(number);
        final int marked =
                count(MeaningQuestions.mark(glossary, "p1", Map.of("p1:brightwater", read), NamePolicy.TRANSLITERATE));

        assertThat(read.explainsName()).isFalse();
        assertThat(marked).isZero();
    }

    // Under Translate the name is already rendered by its meaning, under Keep original it is kept: nothing to ask.
    @ParameterizedTest
    @EnumSource(
            value = NamePolicy.class,
            names = {"TRANSLATE", "KEEP_ORIGINAL"})
    void reading_explainsNameUnderAnotherPolicy_asksNothing(final NamePolicy policy) {
        held("Брайтвотер", TermType.PLACE);

        final int marked = count(MeaningQuestions.mark(glossary, "p1", Map.of("p1:brightwater", reading(2)), policy));

        assertThat(marked).isZero();
        assertThat(stored().meaningChoice()).isNull();
    }

    // A target the person accepted is the person's answer already; a term is no name to ask about.
    @Test
    void reading_explainsNameOfAnAcceptedTargetOrATerm_asksNothing() {
        glossary.add(GlossaryEntry.of(
                "p1:brightwater", "p1", "Brightwater", "Світловод", TermType.CHARACTER, Gender.MALE, false));
        glossary.add(GlossaryEntry.of("p1:ember", "p1", "Ember", null, TermType.TERM, Gender.UNKNOWN, false));

        final int marked = count(MeaningQuestions.mark(
                glossary,
                "p1",
                Map.of("p1:brightwater", reading(2), "p1:ember", reading(2)),
                NamePolicy.TRANSLITERATE));

        assertThat(marked).isZero();
    }

    // Translate drops the suggestion so the next suggestion call asks by meaning; the other answers keep the target.
    @ParameterizedTest
    @CsvSource({"TRANSLATE,", "TRANSCRIBE,Брайтвотер", "TRANSCRIBE_GLOSS,Брайтвотер"})
    void answered_choice_storesItAndKeepsOrDropsTheSuggestion(final MeaningChoice choice, final String target) {
        final GlossaryEntry entry = held("Брайтвотер", TermType.CHARACTER).withMeaningChoice(MeaningChoice.PENDING);

        final GlossaryEntry answered = MeaningQuestions.answered(entry, choice);

        assertThat(answered)
                .extracting(GlossaryEntry::meaningChoice, GlossaryEntry::target, GlossaryEntry::targetOrigin)
                .containsExactly(choice, target, target == null ? null : Origin.LIKELY);
    }
}
