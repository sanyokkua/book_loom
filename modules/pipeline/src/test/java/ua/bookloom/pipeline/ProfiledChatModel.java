package ua.bookloom.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelProfile;

/**
 * A model that answers through a real one but declares a batch size of its own, so a whole-book test starts its first
 * batch where the scenario needs it: a stub provider's model name has no built-in profile row, and the run reads its
 * batch size from the model's profile. Requests are shaped by the delegate, which keeps its own profile.
 */
final class ProfiledChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ModelProfile profile;

    ProfiledChatModel(final ChatModel delegate, final int batchSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        final ModelProfile base = delegate.profile();
        this.profile = new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                batchSize,
                base.tier());
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return delegate.chat(request);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        return delegate.chat(request, attempts);
    }

    @Override
    public @Nullable Integer contextWindowOverride() {
        return delegate.contextWindowOverride();
    }

    @Override
    public ModelProfile profile() {
        return profile;
    }
}
