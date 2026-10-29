package ua.bookloom.pipeline;

import com.google.inject.AbstractModule;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.llm.CallLedgerImpl;

/**
 * Stands in for the {@code :llm} binding {@link PipelineModule} needs and does not own: the call ledger every model
 * call is recorded in. Tests that build an injector without {@code LlmModule} install it beside the pipeline module.
 */
public final class CallLedgerTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CallLedger.class).toInstance(new CallLedgerImpl());
    }
}
