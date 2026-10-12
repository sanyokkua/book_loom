package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedSetupAssistant;

/** The Book Brief hands the suggestion the context window its chosen model really has, so the passages fit it (15i.B3). */
class BookBriefViewModelDetectedContextTest extends ExportViewModelTestBase {

    private static final BriefSuggestion ANSWER =
            new BriefSuggestion("noir", Register.CASUAL, null, null, NarratorPerson.UNSPECIFIED, Gender.UNKNOWN);

    @TempDir
    private Path dir;

    private final ScriptedSetupAssistant assistant = new ScriptedSetupAssistant();
    private final List<String> askedModels = new ArrayList<>();

    @BeforeEach
    void openTheBook() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
        assistant.answersBrief(Result.ok(ANSWER));
    }

    private void suggestWith(final ModelCapabilities detection) {
        brief = onFx(() -> new BookBriefViewModel(
                current,
                projects,
                new DirectExecutor(),
                tag -> true,
                new SetupHelper(assistant, models, settings, new DirectExecutor(), activities, detection)));
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {
            brief.styleSuggestion().ask();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ModelCapabilities reporting(final Result<ContextLength> answer) {
        return (providerId, modelId) -> {
            askedModels.add(modelId);
            return answer;
        };
    }

    // IF the suggestion were sized for a window the model does not have, THEN a 4k model would get 16k of passages.
    @Test
    void suggestStyle_providerReportsTheContext_asksTheAssistantWithIt() {
        suggestWith(reporting(Result.ok(new ContextLength(4096))));

        assertThat(assistant.detectedContexts()).containsExactly(4096);
        assertThat(askedModels).containsExactly("gemma3:12b");
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.DONE);
    }

    @Test
    void suggestStyle_providerSaysNothing_asksTheAssistantWithNoContext() {
        suggestWith(reporting(Result.ok(ContextLength.unknown())));

        assertThat(assistant.detectedContexts()).singleElement().isNull();
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.DONE);
    }

    // IF a failed detection stopped the suggestion, THEN an unreachable catalogue would block a feature that needs
    // none.
    @Test
    void suggestStyle_detectionFails_stillAsksWithNoContext() {
        suggestWith(reporting(Result.err(AppError.of(ErrorCode.unreachable, "Down", "No answer."))));

        assertThat(assistant.detectedContexts()).singleElement().isNull();
        assertThat(onFx(() -> brief.styleSuggestion().outcome().get().kind()))
                .isEqualTo(StyleSuggestionOutcome.Kind.DONE);
    }
}
