package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.pipeline.prompt.FrameWording;
import ua.bookloom.pipeline.prompt.LanguageRules;

class ExamplesTest {

    @Test
    void examples_pairWithoutExamples_targetIllustrationLabelled() {
        final ExampleChoice choice = Examples.choose(LanguageRules.bundled(), "de", "uk", ModelProfile.defaults());

        assertThat(choice.kind()).isEqualTo(ExampleChoice.Kind.TARGET);
        assertThat(choice.text())
                .startsWith("Natural Ukrainian prose")
                .contains(FrameWording.illustration(LanguageRules.bundled(), "uk"));
    }

    @Test
    void examples_pairWithExamples_pairExamples() {
        final ExampleChoice choice = Examples.choose(LanguageRules.bundled(), "ru", "uk", ModelProfile.defaults());

        assertThat(choice.kind()).isEqualTo(ExampleChoice.Kind.PAIR);
        assertThat(choice.text())
                .contains("<s id=\"1\">— Погоди, ⟦g0⟧! — крикнула она.</s>")
                .contains("Reply: {\"items\":[{\"id\":\"1\",\"target\":\"— Стривай, ⟦g0⟧! — гукнула вона.\"}");
    }

    @Test
    void examples_targetWithoutIllustration_none() {
        assertThat(Examples.choose(LanguageRules.bundled(), "uk", "de", ModelProfile.defaults())
                        .kind())
                .isEqualTo(ExampleChoice.Kind.NONE);
    }

    @Test
    void examples_profileExamplesOff_none() {
        final ModelProfile off = FrameFixtures.withQuirks(Set.of(ModelProfile.Quirk.EXAMPLES_OFF));

        final RunFrame frame =
                FrameFixtures.frame(FrameFixtures.book(FrameFixtures.NARRATION), FrameFixtures.brief("ru", "uk"), off);

        assertThat(Examples.choose(LanguageRules.bundled(), "ru", "uk", off).kind())
                .isEqualTo(ExampleChoice.Kind.NONE);
        assertThat(frame.examples().kind()).isEqualTo(ExampleChoice.Kind.NONE);
        assertThat(frame.system()).extracting(Block::slot).doesNotContain(BlockSlots.EXAMPLES);
    }
}
