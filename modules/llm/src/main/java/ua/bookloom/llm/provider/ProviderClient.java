package ua.bookloom.llm.provider;

import java.util.List;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderKind;

/** Internal boundary implemented by each provider-specific HTTP dialect. */
public interface ProviderClient {

    /** Checks provider reachability without listing or loading a model. */
    ProviderCallResult<Boolean> probe();

    /** Returns the provider's available model identifiers, or a typed discovery failure. */
    ProviderCallResult<List<ModelInfo>> listModels();

    /** Sends one non-streaming conversation to the bound model. */
    ProviderCallResult<ChatResponse> chat(String modelId, ChatRequest request);

    /** Identifies the wire dialect implemented by this client. */
    ProviderKind kind();
}
