package ua.bookloom.app.cli;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;

/** Recording seams for translate-command provider tests. */
final class TranslateCommandTestFakes {

    private TranslateCommandTestFakes() {}

    static final class RecordingProviderConfigs implements ProviderConfigs {

        private final ProviderConfig ollama = config("ollama", ProviderKind.OLLAMA, "http://localhost:11434");
        private final Map<String, ProviderConfig> known = new LinkedHashMap<>(Map.of(
                ollama.id(),
                ollama,
                "lmstudio",
                config("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "http://localhost:1234/v1")));
        private final List<ProviderConfig> registered = new ArrayList<>();

        @Override
        public Result<ProviderConfig> register(ProviderConfig config) {
            registered.add(config);
            known.put(config.id(), config);
            return Result.ok(config);
        }

        @Override
        public Optional<ProviderConfig> find(String id) {
            return Optional.ofNullable(known.get(id));
        }

        @Override
        public List<ProviderConfig> all() {
            return List.copyOf(known.values());
        }

        ProviderConfig ollama() {
            return ollama;
        }

        List<ProviderConfig> registered() {
            return List.copyOf(registered);
        }
    }

    static final class ScriptedProviderVerifier implements ProviderVerifier {

        private final VerificationReport report;
        private final List<ModelSelection> selections = new ArrayList<>();
        private final List<VerificationPolicy> policies = new ArrayList<>();

        ScriptedProviderVerifier(VerificationReport report) {
            this.report = report;
        }

        @Override
        public Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy) {
            selections.add(selection);
            policies.add(policy);
            return Result.ok(report);
        }

        List<ModelSelection> selections() {
            return List.copyOf(selections);
        }

        List<VerificationPolicy> policies() {
            return List.copyOf(policies);
        }
    }

    static final class RecordingChatModelFactory implements ChatModelFactory {

        private final List<ModelSelection> selections = new ArrayList<>();

        @Override
        public Result<ChatModel> create(ModelSelection selection) {
            selections.add(selection);
            return Result.ok(request ->
                    Result.ok(new ChatResponse("{\"target\":\"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.\"}", FinishReason.STOP)));
        }

        List<ModelSelection> selections() {
            return List.copyOf(selections);
        }
    }

    private static ProviderConfig config(String id, ProviderKind kind, String baseUrl) {
        return new ProviderConfig(
                id,
                kind,
                URI.create(baseUrl),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }
}
