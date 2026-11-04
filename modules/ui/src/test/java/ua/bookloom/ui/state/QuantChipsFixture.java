package ua.bookloom.ui.state;

import ua.bookloom.ui.ScriptedModelCapabilities;

/** Quant chips over a port that knows nothing, read on the calling thread, for tests that build a view model. */
final class QuantChipsFixture {

    private QuantChipsFixture() {}

    static ModelQuantChips idle() {
        return on(ScriptedModelCapabilities.idle(), new DirectExecutor());
    }

    static ModelQuantChips on(
            final ua.bookloom.api.llm.ModelCapabilities capabilities, final java.util.concurrent.ExecutorService io) {
        return new ModelQuantChips(capabilities, new AdviceReader(capabilities, io), io);
    }
}
