package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;

/** A woman's name in a Ukrainian target is held to feminine verbs and an unchanged form, on invented names. */
class UkrainianCharacterAgreementTest {

    private static final GenderCheck CHECK = GenderChecks.named("uk");
    private static final List<CharacterName> CHISEL = List.of(new CharacterName("Чизел", Gender.FEMALE));

    @Test
    void findCharacters_masculineVerbAfterAWomansName_flagsTheVerbSoftly() {
        final List<CheckFinding> found = CHECK.findCharacters("Чизел налив чаю. Вона втомилася.", CHISEL);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().kind()).isEqualTo(FindingKind.NAME_GENDER);
        assertThat(found.getFirst().blocking()).isFalse();
        assertThat(found.getFirst().span().text()).isEqualTo("налив");
        assertThat(found.getFirst().explanation()).contains("Чизел").contains("feminine");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Чизел налила чаю й подала чашку.",
                "Чизел тихо налила чаю.",
                "Чизел, який налив, пішов.",
                "Чизел і Флінт налили чаю.",
                "Вони покликали Чизел, і вона прийшла."
            })
    void findCharacters_feminineOrUnrelatedWordAfterTheName_isClean(final String text) {
        assertThat(CHECK.findCharacters(text, CHISEL)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "Ніхто не бачив Чизела біля дверей.,Чизела",
        "Ми дали Чизелу чашку.,Чизелу",
        "Він подякував Чизелові за чай.,Чизелові",
        "Чизелового дому там не було.,Чизелового"
    })
    void findCharacters_womansConsonantNameDeclinedLikeAMans_isFlaggedOnTheName(final String text, final String word) {
        assertThat(CHECK.findCharacters(text, CHISEL))
                .extracting(finding -> finding.span().text())
                .containsExactly(word);
    }

    @Test
    void findCharacters_vowelNameDeclinedAsItShould_isClean() {
        final List<CharacterName> liza = List.of(new CharacterName("Ліза", Gender.FEMALE));

        assertThat(CHECK.findCharacters("Ми дали Лізі чашку. Ліза налила чаю.", liza))
                .isEmpty();
    }

    @Test
    void findCharacters_vowelNameWithAMasculineVerb_isFlagged() {
        final List<CharacterName> liza = List.of(new CharacterName("Ліза", Gender.FEMALE));

        assertThat(CHECK.findCharacters("Ліза налив чаю.", liza))
                .extracting(finding -> finding.span().text())
                .containsExactly("налив");
    }

    @Test
    void findCharacters_manOrUnknownGender_isNotChecked() {
        final List<CharacterName> men =
                List.of(new CharacterName("Флінт", Gender.MALE), new CharacterName("Корвін", Gender.UNKNOWN));

        assertThat(CHECK.findCharacters("Флінт налив чаю. Корвін налила чаю.", men))
                .isEmpty();
    }

    @Test
    void findCharacters_nameInsideQuotedSpeech_isNotRead() {
        assertThat(CHECK.findCharacters("«Чизел налив чаю», — сказала вона.", CHISEL))
                .isEmpty();
    }

    @Test
    void findCharacters_languageWithoutACheck_findsNothing() {
        assertThat(GenderChecks.named("xx").findCharacters("Чизел налив чаю.", CHISEL))
                .isEmpty();
    }
}
