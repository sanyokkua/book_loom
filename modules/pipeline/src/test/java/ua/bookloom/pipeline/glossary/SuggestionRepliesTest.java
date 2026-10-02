package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.util.lang.Script;

/** What a suggestion reply may write: one usable rendering per term of its batch, nothing else. */
class SuggestionRepliesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GlossaryEntry HALE =
            new GlossaryEntry("p1:hale", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final Map<String, GlossaryEntry> BATCH = Map.of("hale", HALE);

    private static List<Suggestion> read(final String target) {
        return SuggestionReplies.read(
                MAPPER,
                "{\"suggestions\":[{\"term\":\"Hale\",\"target\":\"" + target + "\",\"gender\":\"male\"}]}",
                BATCH,
                Script.LATIN);
    }

    @Test
    void read_cleanRendering_keepsItStrippedWithItsGender() {
        assertThat(read("  Гейл "))
                .extracting(Suggestion::entry, Suggestion::target, Suggestion::gender)
                .containsExactly(tuple(HALE, "Гейл", Gender.MALE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "Гейл\\nГейлі",
                "⟦g0⟧ Гейл",
                "Hale",
                "Гейлs",
                "Гейллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллллл"
            })
    void read_unusableRendering_isDropped(final String target) {
        assertThat(read(target)).isEmpty();
    }

    @Test
    void read_latinLookAlikeInsideACyrillicWord_isRepairedAndKept() {
        assertThat(read("Гeйл")).extracting(Suggestion::target).containsExactly("Гейл");
    }

    @Test
    void read_anyScriptAllowed_keepsALatinRendering() {
        final List<Suggestion> kept = SuggestionReplies.read(
                MAPPER, "{\"suggestions\":[{\"term\":\"hale\",\"target\":\"Hale\",\"gender\":\"x\"}]}", BATCH, null);

        assertThat(kept)
                .extracting(Suggestion::target, Suggestion::gender)
                .containsExactly(tuple("Hale", Gender.UNKNOWN));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"suggestions\":[{\"term\":\"Moreau\",\"target\":\"Моро\",\"gender\":\"male\"}]}",
                "{\"verdicts\":[]}",
                "not json at all"
            })
    void read_termOutsideTheBatchOrNoSuggestions_keepsNothing(final String reply) {
        assertThat(SuggestionReplies.read(MAPPER, reply, BATCH, null)).isEmpty();
    }
}
