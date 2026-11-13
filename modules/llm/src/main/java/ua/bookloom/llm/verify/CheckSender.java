package ua.bookloom.llm.verify;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.llm.engine.EngineTraits.OffSwitch;
import ua.bookloom.llm.provider.CheckReply;
import ua.bookloom.llm.provider.ProviderCallResult;

/** Sends one call of the Advanced features checks through the gate, for {@link AdvancedChecks}. */
@FunctionalInterface
interface CheckSender {

    /**
     * Sends one check.
     *
     * @param check the check the call belongs to, so the test card can list the call under it
     * @param offSwitch the thinking switch to send in place of the engine's, or null for the engine's own
     */
    ProviderCallResult<CheckReply> send(AdvancedCheck check, ChatRequest request, @Nullable OffSwitch offSwitch);
}
