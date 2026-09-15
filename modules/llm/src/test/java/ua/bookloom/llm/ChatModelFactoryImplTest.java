package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelSelection;

/**
 * The pseudo chat model and its provider/model factory seam.
 */
class ChatModelFactoryImplTest {

    private static final ChatModelFactory FACTORY = new ChatModelFactoryImpl();

    // The pseudo provider is the deterministic model used by the first pipeline task.
    @Test
    void create_pseudoUppercase_returnsUppercaseStopResponse() {
        final ChatModel model = pseudoModel();

        final Result<ChatResponse> result =
                model.chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))));

        assertThat(responseOf(result).content()).isEqualTo("HELLO");
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // An unsupported provider id is a typed validation refusal, not a model or an exception.
    @Test
    void create_unknownProvider_returnsValidationError() {
        final Result<ChatModel> result = FACTORY.create(new ModelSelection("ollama", "qwen3:8b"));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // A blank model id cannot bind a chat model, even when the provider id is known.
    @Test
    void create_blankModelId_returnsValidationError() {
        final Result<ChatModel> result = FACTORY.create(new ModelSelection("pseudo", " "));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // An empty model id is refused like a blank one, never taken to mean "use a default model".
    @Test
    void create_emptyModelId_returnsValidationError() {
        final Result<ChatModel> result = FACTORY.create(new ModelSelection("pseudo", ""));

        assertThat(result.data()).isNull();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // Protected document placeholders and entity references survive the pseudo translation byte-for-byte.
    @Test
    void chat_placeholderAndEntityReferences_preservesReferencesWhileUppercasingText() {
        final Result<ChatResponse> result = pseudoModel()
                .chat(new ChatRequest(
                        List.of(new ChatMessage(ChatRole.USER, "Tom ⟦g0⟧ran⟦g1⟧ &amp; hid&nbsp;&#160;&#xA0;⟦g12⟧"))));

        assertThat(responseOf(result).content()).isEqualTo("TOM ⟦g0⟧RAN⟦g1⟧ &amp; HID&nbsp;&#160;&#xA0;⟦g12⟧");
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // Locale.ROOT makes uppercase conversion stable when the process default locale is Turkish.
    @ResourceLock(Resources.LOCALE)
    @Test
    void chat_turkishDefaultLocale_usesRootUppercase() {
        final Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            final Result<ChatResponse> result =
                    pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "title"))));

            assertThat(responseOf(result).content()).isEqualTo("TITLE");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    // A conversation is answered from its final user turn, not from system or earlier assistant content.
    @Test
    void chat_multipleUserMessages_answersLastUserMessage() {
        final Result<ChatResponse> result = pseudoModel()
                .chat(new ChatRequest(List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Translate into uk"),
                        new ChatMessage(ChatRole.USER, "one"),
                        new ChatMessage(ChatRole.ASSISTANT, "ONE"),
                        new ChatMessage(ChatRole.USER, "two"))));

        assertThat(responseOf(result).content()).isEqualTo("TWO");
    }

    // An empty final user turn is still a successful normal completion.
    @Test
    void chat_emptyLastUserMessage_returnsEmptyStopResponse() {
        final Result<ChatResponse> result =
                pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, ""))));

        assertThat(responseOf(result).content()).isEmpty();
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // A conversation without a user turn has the same empty normal completion as an empty final user turn.
    @Test
    void chat_withoutUserMessage_returnsEmptyStopResponse() {
        final Result<ChatResponse> result =
                pseudoModel().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.SYSTEM, "Translate into uk"))));

        assertThat(responseOf(result).content()).isEmpty();
        assertThat(responseOf(result).finishReason()).isEqualTo(FinishReason.STOP);
    }

    // The LLM Guice module exposes the provider-neutral factory binding used by callers.
    @Test
    void configure_llmModule_resolvesChatModelFactoryBinding() {
        final Injector injector = Guice.createInjector(new LlmModule());

        assertThat(injector.getInstance(ChatModelFactory.class)).isInstanceOf(ChatModelFactoryImpl.class);
    }

    private static ChatModel pseudoModel() {
        return Objects.requireNonNull(
                FACTORY.create(new ModelSelection("pseudo", "uppercase")).data(), "pseudo model");
    }

    private static ChatResponse responseOf(Result<ChatResponse> result) {
        return Objects.requireNonNull(result.data(), "response");
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
