package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.checks.CharacterNames;
import ua.bookloom.pipeline.context.InjectedCharacters;
import ua.bookloom.pipeline.memory.TermFacts;

/**
 * The names pass asks the model for the most probable gender of the names nobody settled, stores each as likely with
 * its confidence, and nothing that checks a translation reads it: neither a likely gender nor a rule's reaches the
 * sheet the gender check is held to (design D5).
 */
class NamesPassKnowledgeTest extends NamesPassTestBase {

    private static final String SCENE = "Ivar and Tamsin crossed the yard.";

    private void seededWithBareNames() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        fixture.glossary.add(GlossaryEntry.of(
                GlossaryIds.of(PROJECT, "Ivar"), PROJECT, "Ivar", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        fixture.glossary.add(GlossaryEntry.of(
                        GlossaryIds.of(PROJECT, "Tamsin"),
                        PROJECT,
                        "Tamsin",
                        null,
                        TermType.CHARACTER,
                        Gender.UNKNOWN,
                        false)
                .withGenderFrom(Gender.FEMALE, Origin.RULE));
    }

    @Test
    void pass_barenames_storeLikelyWithConfidenceAndNeverEnforce() {
        seededWithBareNames();
        final NamesPassModel model =
                model().knows("Ivar", "male", "person", 0.55).knows("Tamsin", "male", "person", 0.8);

        succeeded(run(model));

        final NameKnowledge ivar = new NameKnowledge(Gender.MALE, TermType.CHARACTER, null, 0.55);
        assertThat(held("Ivar"))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin, GlossaryEntry::knowledge)
                .containsExactly(Gender.MALE, Origin.LIKELY, ivar);
        assertThat(ivar.isLowConfidenceFor(Gender.MALE, Origin.LIKELY)).isTrue();
        final NameKnowledge tamsin = new NameKnowledge(Gender.MALE, TermType.CHARACTER, null, 0.8);
        assertThat(held("Tamsin"))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin, GlossaryEntry::knowledge)
                .containsExactly(Gender.FEMALE, Origin.RULE, tamsin);
        assertThat(tamsin.disagreesWithRule(Gender.FEMALE, Origin.RULE)).isTrue();
        assertThat(model.kinds()).contains(CallKind.NAME_KNOWLEDGE);
        final List<String> sheet = InjectedCharacters.selectIn(List.of(SCENE), fixture.glossaryNow(), "en");
        assertThat(sheet).containsExactlyInAnyOrder("Ivar", "Tamsin");
        assertThat(CharacterNames.of(sheet, List.of("Ivar → Івар", "Tamsin → Тамсін")))
                .isEmpty();
    }

    // A rule's gender helps the model on the glossary line; a likely one is not put in any prompt by default.
    @Test
    void glossaryLine_ruleGenderStatedAndLikelyGenderLeftOut() {
        assertThat(TermFacts.of(TermType.CHARACTER, Gender.FEMALE, Origin.RULE)).isEqualTo("character, female");
        assertThat(TermFacts.of(TermType.CHARACTER, Gender.MALE, Origin.LIKELY)).isEqualTo("character");
    }
}
